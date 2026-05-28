package com.anjo.service
import com.anjo.effect.BlinkEffect
import com.anjo.effect.EffectRenderer
import com.anjo.effect.FadeEffect
import com.anjo.effect.ReverseEffect
import com.anjo.effect.ScrollEffect
import com.anjo.model.Effect
class EffectRendererFactory {
    fun create(effect: Effect): EffectRenderer = when (effect) {
        Effect.SCROLL -> ScrollEffect()
        Effect.BLINK -> BlinkEffect()
        Effect.REVERSE -> ReverseEffect()
        Effect.FADE -> FadeEffect()
    }
}
