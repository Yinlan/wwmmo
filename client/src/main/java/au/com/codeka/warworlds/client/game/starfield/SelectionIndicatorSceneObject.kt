package au.com.codeka.warworlds.client.game.starfield

import au.com.codeka.warworlds.client.opengl.DimensionResolver
import au.com.codeka.warworlds.client.opengl.SceneObject
import au.com.codeka.warworlds.common.Colour

/**
 * A [SceneObject] that we can add to a star or fleet to indicate that it's the currently-
 * selected one.
 */
class SelectionIndicatorSceneObject(dimensionResolver: DimensionResolver?)
  : BaseIndicatorSceneObject(dimensionResolver, Colour.WHITE, 5.0f)