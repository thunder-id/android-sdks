/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.thunderid.android.logo

import androidx.annotation.DrawableRes
import dev.thunderid.android.R

/**
 * Maps the curated `avatar:...,variant=anonymous_animal,content=<name>` (or
 * `variant=anonymous_entity,content=<name>`) logo spec names to the drawable resources bundled
 * with the SDK. Lookups are case-sensitive, matching the lowercase keys used on the wire (e.g.
 * `"jackalope"`, not `"Jackalope"`).
 */
internal object LogoIconRegistry {
    val ANONYMOUS_ANIMAL_ICONS: Map<String, Int> =
        mapOf(
            "jackalope" to R.drawable.logo_icon_anonymous_jackalope,
            "mink" to R.drawable.logo_icon_anonymous_mink,
            "otter" to R.drawable.logo_icon_anonymous_otter,
            "platypus" to R.drawable.logo_icon_anonymous_platypus,
            "quagga" to R.drawable.logo_icon_anonymous_quagga,
            "raccoon" to R.drawable.logo_icon_anonymous_raccoon,
            "skunk" to R.drawable.logo_icon_anonymous_skunk,
            "chameleon" to R.drawable.logo_icon_anonymous_chameleon,
            "dingo" to R.drawable.logo_icon_anonymous_dingo,
            "hedgehog" to R.drawable.logo_icon_anonymous_hedgehog,
            "dinosaur" to R.drawable.logo_icon_anonymous_dinosaur,
            "capybara" to R.drawable.logo_icon_anonymous_capybara,
            "chinchilla" to R.drawable.logo_icon_anonymous_chinchilla,
            "chipmunk" to R.drawable.logo_icon_anonymous_chipmunk,
            "chupacabra" to R.drawable.logo_icon_anonymous_chupacabra,
            "frog" to R.drawable.logo_icon_anonymous_frog,
            "giraffe" to R.drawable.logo_icon_anonymous_giraffe,
            "hippo" to R.drawable.logo_icon_anonymous_hippo,
            "jackal" to R.drawable.logo_icon_anonymous_jackal,
        )

    val ANONYMOUS_ENTITY_ICONS: Map<String, Int> =
        mapOf(
            "hexagon" to R.drawable.logo_icon_entity_hexagon,
            "diamond" to R.drawable.logo_icon_entity_diamond,
            "cube" to R.drawable.logo_icon_entity_cube,
            "pentagon" to R.drawable.logo_icon_entity_pentagon,
            "triangle_stack" to R.drawable.logo_icon_entity_triangle_stack,
            "chevron" to R.drawable.logo_icon_entity_chevron,
            "orbit_ring" to R.drawable.logo_icon_entity_orbit_ring,
            "octagon" to R.drawable.logo_icon_entity_octagon,
            "star" to R.drawable.logo_icon_entity_star,
            "parallelogram" to R.drawable.logo_icon_entity_parallelogram,
            "plus_facet" to R.drawable.logo_icon_entity_plus_facet,
            "spiral" to R.drawable.logo_icon_entity_spiral,
            "townhouse" to R.drawable.logo_icon_entity_townhouse,
            "tower" to R.drawable.logo_icon_entity_tower,
            "dome" to R.drawable.logo_icon_entity_dome,
            "gate" to R.drawable.logo_icon_entity_gate,
            "pavilion" to R.drawable.logo_icon_entity_pavilion,
            "spire" to R.drawable.logo_icon_entity_spire,
            "bridge" to R.drawable.logo_icon_entity_bridge,
            "lighthouse" to R.drawable.logo_icon_entity_lighthouse,
            "windmill" to R.drawable.logo_icon_entity_windmill,
            "silo" to R.drawable.logo_icon_entity_silo,
            "arch" to R.drawable.logo_icon_entity_arch,
            "obelisk" to R.drawable.logo_icon_entity_obelisk,
            "turbine" to R.drawable.logo_icon_entity_turbine,
            "anchor" to R.drawable.logo_icon_entity_anchor,
            "anvil" to R.drawable.logo_icon_entity_anvil,
            "compass" to R.drawable.logo_icon_entity_compass,
            "key" to R.drawable.logo_icon_entity_key,
            "lock" to R.drawable.logo_icon_entity_lock,
            "circuit_node" to R.drawable.logo_icon_entity_circuit_node,
            "antenna" to R.drawable.logo_icon_entity_antenna,
            "valve" to R.drawable.logo_icon_entity_valve,
        )

    @DrawableRes
    fun anonymousAnimalIconOrNull(name: String): Int? = ANONYMOUS_ANIMAL_ICONS[name]

    @DrawableRes
    fun anonymousEntityIconOrNull(name: String): Int? = ANONYMOUS_ENTITY_ICONS[name]
}
