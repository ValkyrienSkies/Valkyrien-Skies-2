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
  isPathFindable of getPathType, determines possible nodes in water
* `SwimNodeEvaluatorMixin#getFluidStateRedirectIsFree` Redirects
  getFluidState of isFree, determines water in open spaces
* `SwimNodeEvaluatorMixin#getBlockStateRedirectIsFree` Redirects
  getBlockState of isFree, determines blockages in open spaces
* `SwimNodeEvaluatorMixin#isPathFindableRedirectIsFree` Redirects
  isPathFindable of isFree, determines possible nodes in water
* `WalkNodeEvaluatorMixin#getBlockPathTypeForShips` Redirects
  getBlockPathTypeRaw to respect ships
