package com.anjo.service
import com.anjo.service.effect.EffectRenderer
import com.anjo.model.Effect
import com.anjo.service.effect.BlinkEffect
import com.anjo.service.effect.FadeEffect
import com.anjo.service.effect.ReverseEffect
import com.anjo.service.effect.ScrollEffect

class EffectRendererFactory {
    fun create(effect: Effect): EffectRenderer = when (effect) {
        Effect.SCROLL -> ScrollEffect()
        Effect.BLINK -> BlinkEffect()
        Effect.REVERSE -> ReverseEffect()
        Effect.FADE -> FadeEffect()
    }
}
