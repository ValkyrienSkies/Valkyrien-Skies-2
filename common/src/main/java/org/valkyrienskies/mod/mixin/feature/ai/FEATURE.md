### AI

This feature adds a set of subfeatures which allows for pathfinding to work with
ships.

#### Subfeatures

* `node_evaluator` Allows grid-aligned nodes to recognize ships as walkable
  obstacles.

#### Mixins

* `PathNavigationRegionAccessor#getLevel` Allows access to the
  PathNavigationRegion's proper level
* `SwimNodeEvaluatorMixin#getFluidStateRedirectPathType` Redirects getFluidState
  of getPathType, allows for swim nodes
* `SwimNodeEvaluatorMixin#getBlockStateRedirectPathType` Redirects getBlockState
  of getPathType, allows for obstacle nodes
* `SwimNodeEvaluatorMixin#isPathFindableRedirectPathType` Redirects
  isPathFindable of getPathType, projects ship-local cells in InShip frame and
  overlays ship blocks on world cells in World frame
* `SwimNodeEvaluatorMixin#getFluidStateRedirectGetNode` Redirects getFluidState
  of findAcceptedNode through the active frame
* `MixinPathNavigationRegion#vs$frameAwareGetBlockState` Frame-aware
  getBlockState peek for per-cell A* reads
* `MixinPathNavigationRegion#vs$frameAwareGetFluidState` Frame-aware
  getFluidState peek for per-cell A* reads
* `MixinWalkNodeEvaluator#vs$shipLocalStart` Anchors the walk-mob
  start node in ship-local coords for InShip-frame searches
* `MixinSwimNodeEvaluatorGetStart#vs$shipLocalStart` Same anchor for swim mobs
* `MixinAmphibiousNodeEvaluatorGetStart#vs$shipLocalStart` Same anchor for
  amphibious mobs
* `MixinFlyNodeEvaluatorGetStart#vs$shipLocalStart` Same anchor for flying mobs
* `MixinFrogNodeEvaluatorGetStart#vs$shipLocalStart` Same anchor for the frog's
  in-water node evaluator
* `MixinWalkNodeEvaluator#vs$shipFrameCollisionSweep` Runs
  canReachWithoutCollision in ship-local frame so the swept-AABB delta doesn't
  blow up at shipyard scale
* `MixinPathFinder#onCollectPath` Keeps ship-local target coords intact in
  InShip-frame searches; legacy world-coord callers still go through the
  world-frame transform
* `MixinPathNavigation#vs$wrapFindPath` Sets up the per-frame query state and
  routes the search through the right frame
* `MixinPathNavigation#vs$bypassGroundSnapForShipMobs` Skips the world-floor
  ground snap for entities being dragged by a ship
* `MixinPathNavigation#vs$getBlockStateIsNotStable` Frame-aware
  isStableDestination block read
* `MixinGroundPathNavigation#vs$gateIsAirOnShip` Skips createPath's air-scan
  branch for targets inside a ship's worldAABB so the target Y isn't bumped
  to maxBuildHeight
* `MixinPath#vs$reproject*` Projects per-node path coords through each node's
  stored frame so vanilla consumers see world positions
* `MixinPathNavigationFollow#vs$followInShipFrame` Runs the followThePath
  advance check in ship-local for InShip-frame paths so the within-distance
  gate respects the ship's grid orientation
* `MixinMoveControl#vs$skipBlockBelowWhenOnShip` Stops MoveControl reading
  the world block below a dragged mob (which is air) when computing jump-up
  height
* `MixinMoveControl#vs$gateJumpOnCollisionForShipMobs` Suppresses the
  step-up jump for dragged mobs when the world floor is air
* `MixinDefaultRandomPos#vs$retryWithShipProjection` TAIL retry that
  validates vanilla's failed candidate against ship-local cells when the
  candidate falls inside a ship's worldAABB
* `MixinDefaultRandomPos#vs$projectGenerateRandomPosToWorld` Projects
  shipyard-coord random-pos results to world coords for getPos / getPosTowards
* `MixinAirAndWaterRandomPos#vs$retryWithShipProjection` Same TAIL retry
  pattern for AirAndWaterRandomPos.generateRandomPos
* `MixinLandRandomPos#postGenerateRandomPosTowardDirection` Same TAIL retry
  pattern for LandRandomPos.generateRandomPosTowardDirection
* `MixinLandRandomPos#redirectGetPosInDirection` Projects shipyard-coord
  random-pos results to world coords for getPosInDirection
* `MixinLandRandomPos#preGetPos` TAIL retry on getPos that re-generates
  with the caller-supplied scoring function and returns the world projection
