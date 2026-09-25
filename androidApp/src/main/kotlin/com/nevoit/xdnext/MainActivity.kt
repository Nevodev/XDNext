package com.nevoit.xdnext

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nevoit.xdnext.utility.clearBackground

/**
 * The single activity.
 *
 * Navigation is not yet introduced, so this simply hosts [App]. There is deliberately no `@Preview`
 * wrapper here: [App] resolves its dependencies from Koin, which is started by
 * [XdNextApplication], so a preview would fail to render.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }

        clearBackground()
    }
}
