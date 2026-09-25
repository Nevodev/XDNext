package com.nevoit.material.navigation.shape

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Skiko's answer to [deviceCornerShape]: a rectangle, always.
 *
 * Not a stub waiting to be filled in. iOS has no public API for the display's corner radius, and this
 * was checked rather than assumed:
 *
 *  - the Flutter plugin that does this (`corner_radius_plugin`) reads a **bundled device-model table**
 *    on iOS — BezelKit's `bezel.min.json`, looked up by `getModelIdentifier` and refreshed weekly by a
 *    GitHub Action — while using the platform API on Android. Nobody maintains a lookup table for a
 *    value the system will hand over;
 *  - `UIScreen._displayCornerRadius` exists but is a private key reached through KVC, and Apple's own
 *    developer forum treats it as such — the App Store review risk is not worth a corner;
 *  - iOS 26's `UICornerConfiguration` / SwiftUI's `containerShape` do have a `concentric` mode, but they
 *    *propagate* corner geometry from a container to its children. That is not a query for the panel's
 *    radius, and it is a UIKit/SwiftUI mechanism — Compose Multiplatform on iOS renders through Skia
 *    rather than a `UIView` hierarchy, so it is not reachable from here either.
 *
 * A desktop window has no hardware-rounded panel corners at all, and a browser viewport certainly does
 * not, so the same answer covers every Skiko target: a rectangle is the truthful shape.
 *
 * If Apple ever exposes the radius, this file is the only one that changes — the shared declaration and
 * every call site stay as they are.
 *
 * The parameters are accepted and ignored so that one call site compiles for every target; a caller
 * asking for the device's corners on a device that has none is asking a question with one answer.
 */
@Composable
actual fun deviceCornerShape(
    padding: Dp,
    topLeft: Boolean,
    topRight: Boolean,
    bottomRight: Boolean,
    bottomLeft: Boolean,
): Shape = RectangleShape
