package com.meta.wearable.retail.glimmer

import com.meta.wearable.dat.display.views.*
import com.meta.wearable.retail.ui.Product

/**
 * Industrial UI Builders for Meta Wearables.
 * Flattened architecture to prevent D8 Dexer path length overflows.
 */

fun ContentScope.buildWelcome(onStart: () -> Unit) {
    flexBox(direction = Direction.COLUMN, gap = 16, padding = 24, background = FlexBoxBackground.NONE) {
        text("Vaultier Concierge", style = TextStyle.HEADING)
        button(
            label = "Start Discovery",
            style = ButtonStyle.PRIMARY,
            iconName = IconName.EYE,
            onClick = onStart
        )
    }
}

fun ContentScope.buildCart(products: List<Product>) {
    flexBox(direction = Direction.COLUMN, gap = 12, padding = 16, background = FlexBoxBackground.NONE) {
        products.forEach { product ->
            flexBox(direction = Direction.ROW, gap = 12, padding = 16, background = FlexBoxBackground.CARD) {
                text(product.name, style = TextStyle.BODY)
                text("$${product.price}", style = TextStyle.META)
                icon(name = IconName.CART, style = IconStyle.FILLED)
            }
        }
    }
}

fun ContentScope.buildSuccess() {
    flexBox(direction = Direction.COLUMN, gap = 16, padding = 24, alignment = Alignment.CENTER, background = FlexBoxBackground.NONE) {
        icon(name = IconName.CHECKMARK, style = IconStyle.FILLED)
        text("Purchase Verified", style = TextStyle.HEADING)
        text("Preparing objects...", style = TextStyle.BODY)
    }
}

fun ContentScope.buildStreaming(isOn: Boolean) {
    val iconName = if (isOn) IconName.VIDEO_CAMERA else IconName.VIDEO_CAMERA_OFF
    val statusText = if (isOn) "Live Pulse: Active" else "Pulse: Standby"
    
    flexBox(direction = Direction.COLUMN, gap = 8, padding = 12, alignment = Alignment.END, background = FlexBoxBackground.NONE) {
        flexBox(direction = Direction.ROW, gap = 8) {
            icon(name = iconName, style = IconStyle.FILLED)
            text(statusText, style = TextStyle.META)
        }
    }
}
