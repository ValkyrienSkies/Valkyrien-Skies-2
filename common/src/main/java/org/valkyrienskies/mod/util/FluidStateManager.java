package org.valkyrienskies.mod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.valkyrienskies.mod.mixinducks.world.chunk.LevelChunkDuck;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.StampedLock;

public class FluidStateManager {
	private FluidStateManager() {}

	public static FluidData getFluidData(final Level level, final BlockPos pos) {
		final ChunkAccess chunk = level.getChunk(
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ()),
			ChunkStatus.FULL,
			false
		);
		if (chunk == null) {
			return null;
		}
		final ChunkFluidData fluidData = ((LevelChunkDuck) (chunk)).vs$getFluidData();
		return fluidData.getFluidData(pos);
	}

	private static FluidState getFullFluidState(final FluidState state) {
		// hope the source fluid has max height
		return state.getType() instanceof final FlowingFluid fluid ? fluid.getSource(false) : state;
	}

	public static final class ChunkFluidData {
		private static final int COLUMNS_SIZE = 16 * 16;
		// TODO: right now it assumes all fluids are proper FlowingFluid implemention.
		private final AtomicReferenceArray<Column> columns = new AtomicReferenceArray<>(COLUMNS_SIZE);

		private FluidData getFluidData(final BlockPos pos) {
			final int
				x = SectionPos.sectionRelative(pos.getX()),
				z = SectionPos.sectionRelative(pos.getZ());
			final int index = (x << 4) | z;

			final Column column = this.columns.get(index);
			if (column == null) {
				return null;
			}
			return column.getFluidData(pos.getY());
		}

		/**
		 * setFluidState must always called from the same thread
		 */
		public void setFluidState(final BlockPos pos, final FluidState state) {
			final int
				x = SectionPos.sectionRelative(pos.getX()),
				z = SectionPos.sectionRelative(pos.getZ());
			final int index = (x << 4) | z;
			Column column = this.columns.get(index);
			if (column == null) {
				if (state.isEmpty()) {
					return;
				}
				column = new Column();
				this.columns.set(index, column);
			}
			column.setFluidState(pos.getY(), state);
		}

		public void clear() {
			for (int i = 0; i < COLUMNS_SIZE; i++) {
				this.columns.set(i, null);
			}
		}
	}

	private static final class Column {
		private final StampedLock lock = new StampedLock();
		private volatile List<Section> sections = new ArrayList<>();

		private FluidData getFluidData(final int y) {
			long stamp = this.lock.tryOptimisticRead();
			FluidData data = this.getFluidDataLocked(this.sections, y);

			if (!this.lock.validate(stamp)) {
				stamp = this.lock.readLock();
				try {
					data = this.getFluidDataLocked(this.sections, y);
				} finally {
					this.lock.unlockRead(stamp);
				}
			}
			return data;
		}

		private FluidData getFluidDataLocked(final List<Section> sections, final int y) {
			final int size = sections.size();
			for (int i = 0; i < size; i++) {
				final Section s = sections.get(i);
				if (s.lowY > y) {
					break;
				}
				if (s.highY < y) {
					continue;
				}
				return new FluidData(s.surface, s.highY == y);
			}
			return null;
		}

		public void setFluidState(final int y, final FluidState state) {
			long stamp = this.lock.writeLock();
			try {
				this.setFluidStateLocked(y, state);
			} finally {
				this.lock.unlockWrite(stamp);
			}
		}

		public void setFluidStateLocked(final int y, final FluidState state) {
			final boolean isEmpty = state.isEmpty();
			int i = 0;
			for (final Section s : this.sections) {
				i++;
				if (s.lowY > y) {
					break;
				}
				if (s.highY < y) {
					continue;
				}

				final boolean isSurface = s.highY == y;
				final boolean isBottom = s.lowY == y;
				if (isSurface) {
					if (isBottom) {
						if (isEmpty) {
							final List<Section> newSections = new ArrayList<>(this.sections);
							newSections.remove(i - 1);
							this.sections = newSections;
							return;
						}
						s.surface = state;
						return;
					}
					if (s.surface.getType().isSame(state.getType())) {
						s.surface = state;
						return;
					}
					s.highY--;
					s.surface = getFullFluidState(s.surface);
					if (isEmpty) {
						return;
					}
					// Put current state to the section above if possible
					if (i < this.sections.size()) {
						final Section above = this.sections.get(i);
						if (above.lowY - 1 == y && above.surface.getType().isSame(state.getType())) {
							above.lowY--;
							return;
						}
					}
					this.sections.add(i, new Section(y, state));
					return;
				}
				if (s.surface.getType().isSame(state.getType())) {
					return;
				}
				if (!isEmpty) {
					this.sections.add(i - 1, new Section(y, state));
				}
				if (isBottom) {
					s.lowY++;
					return;
				}
				this.sections.add(i - 1, new Section(s.lowY, y - 1, getFullFluidState(s.surface)));
				s.lowY = y + 1;
				return;
			}
			if (!isEmpty) {
				this.sections.add(i, new Section(y, state));
			}
		}

		private static final class Section {
			private int lowY;
			private int highY;
			private FluidState surface;

			private Section(final int lowY, final int highY, final FluidState fluid) {
				this.lowY = lowY;
				this.highY = highY;
				this.surface = fluid;
			}

			private Section(final int y, final FluidState fluid) {
				this(y, y, fluid);
			}
		}
	}

	public record FluidData(FluidState surface, boolean isSurface) {
		public float height() {
			return this.isSurface ? this.surface.getOwnHeight() : 1.0f;
		}

		public Fluid sourceFluid() {
			final Fluid fluid = this.surface.getType();
			if (fluid instanceof final FlowingFluid flowing) {
				return flowing.getSource();
			}
			return fluid;
		}
	}
}
