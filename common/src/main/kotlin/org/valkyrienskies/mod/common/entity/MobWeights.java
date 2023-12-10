package org.valkyrienskies.mod.common.entity;

public enum MobWeights {
    NORMAL_PLAYER(58.0);

    public double weight;
    MobWeights(final Double weight) {
        this.weight = weight;
    }
}
