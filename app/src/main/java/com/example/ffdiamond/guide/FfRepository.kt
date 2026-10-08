package com.example.ffdiamond.guide

import com.example.ffdiamond.R

/**
 * Offline guide content. Images point at aliases in res/values/ff_images.xml so real artwork
 * can be dropped in without touching this file.
 */
object FfRepository {

    fun items(category: FfCategory): List<FfItem> = when (category) {
        FfCategory.CHARACTERS -> characters
        FfCategory.PETS -> pets
        FfCategory.BUNDLES -> bundles
        FfCategory.WEAPONS -> weapons
        FfCategory.VEHICLES -> vehicles
        FfCategory.EMOTES -> emotes
        FfCategory.PARACHUTES -> parachutes
        FfCategory.TIPS -> tips
    }

    private val characters = listOf(
        FfItem(
            "alok", "ALOK", R.drawable.ff_char_alok,
            "Ability: Drop the Beat (Active)",
            "Creates a 5m aura that boosts movement speed and restores HP every second for everyone inside the zone.",
            "Alok is one of the most picked characters in ranked matches. The healing aura keeps the squad alive during pushes and rotations, which makes him a strong all-round choice for solo and squad modes."
        ),
        FfItem(
            "chrono", "CHRONO", R.drawable.ff_char_chrono,
            "Ability: Time Turner (Active)",
            "Creates a force field that blocks up to 600 damage from enemies. Allies inside get a short movement speed boost.",
            "Chrono is built for aggressive fights. Drop the shield the moment you are caught in the open, then reposition or revive a teammate safely behind it."
        ),
        FfItem(
            "captain_booyah", "K (CAPTAIN BOOYAH)", R.drawable.ff_char_captain_booyah,
            "Ability: Master of All (Active)",
            "Switches between Jiu-jitsu mode, which raises allies' EP conversion rate, and Psychology mode, which slowly recovers EP. Max EP is also increased.",
            "K suits players who rely on EP and healing. Keep Psychology mode on between fights to refill EP, then switch to Jiu-jitsu mode when the squad is grouped up."
        ),
        FfItem(
            "skyler", "SKYLER", R.drawable.ff_char_skyler,
            "Ability: Riptide Rhythm (Active)",
            "Unleashes a sonic wave that destroys Gloo Walls in front of him. Every Gloo Wall he deploys also restores a little HP.",
            "Skyler counters players who hide behind Gloo Walls. Break their cover first, then rush in before they can rebuild it."
        ),
        FfItem(
            "dimitri", "DIMITRI", R.drawable.ff_char_dimitri,
            "Ability: Healing Heartbeat (Active)",
            "Creates a 3.5m healing zone that restores HP over time. Knocked-down allies inside the zone can recover by themselves.",
            "Dimitri is a support pick that turns any building into a safe spot. Great for squads that like to play slow and hold strong positions."
        ),
        FfItem(
            "a124", "A124", R.drawable.ff_char_a124,
            "Ability: Thrill of Battle (Active)",
            "Releases an electromagnetic wave that temporarily disables nearby enemies' character skills.",
            "A124 shuts down skill-heavy opponents. Use the wave right before a push so enemies cannot answer with their own abilities."
        ),
        FfItem(
            "xayne", "XAYNE", R.drawable.ff_char_xayne,
            "Ability: Xtreme Encounter (Active)",
            "Gains temporary HP that decays over time and deals extra damage to Gloo Walls and shields for a short duration.",
            "Xayne is made for rushing. The temporary HP lets you survive the first trade, and the bonus wall damage helps you break defensive setups."
        ),
        FfItem(
            "wukong", "WUKONG", R.drawable.ff_char_wukong,
            "Ability: Camouflage (Active)",
            "Transforms into a bush for a few seconds, moving slower but staying hidden. The cooldown resets after a kill.",
            "Wukong is ideal for stealthy players. Hide in open fields, avoid third parties and surprise enemies who walk past you."
        ),
        FfItem(
            "homer", "HOMER", R.drawable.ff_char_homer,
            "Ability: Senses Shockwave (Active)",
            "Launches a drone that flies to the nearest enemy, dealing damage and slowing their movement and reload speed.",
            "Homer is great at finding and weakening opponents behind cover. Fire the drone first and push while the target is slowed."
        ),
        FfItem(
            "moco", "MOCO", R.drawable.ff_char_moco,
            "Ability: Hacker's Eye (Passive)",
            "Tags enemies you shoot for a few seconds and shares their location with your teammates.",
            "Moco gives the whole squad information. She pairs well with active-skill characters and is a favourite for team play."
        ),
        FfItem(
            "kelly", "KELLY", R.drawable.ff_char_kelly,
            "Ability: Dash (Passive)",
            "Increases sprinting speed, helping you rotate faster and escape the shrinking safe zone.",
            "Kelly is a simple but useful pick for fast movement. Great for players who love to roam and take quick fights."
        ),
        FfItem(
            "hayato", "HAYATO", R.drawable.ff_char_hayato,
            "Ability: Bushido (Passive)",
            "As HP drops, armor penetration increases, so you deal more damage when you are low on health.",
            "Hayato rewards bold players. When a fight goes badly, his skill gives you a real chance to turn it around."
        ),
        FfItem(
            "laura", "LAURA", R.drawable.ff_char_laura,
            "Ability: Sharp Shooter (Passive)",
            "Increases accuracy while scoped in, making long-range shots more consistent.",
            "Laura suits snipers and marksman players who like to fight from a distance with scoped weapons."
        ),
        FfItem(
            "rafael", "RAFAEL", R.drawable.ff_char_rafael,
            "Ability: Dead Silent (Passive)",
            "Sniper and marksman rifle shots are silenced, and knocked-down enemies lose HP faster.",
            "Rafael is a stealth sniper pick. Silenced shots keep your position hidden while you thin out enemy squads."
        ),
        FfItem(
            "nikita", "NIKITA", R.drawable.ff_char_nikita,
            "Ability: Firearms Expert (Passive)",
            "Reloads SMGs faster and deals bonus damage with them.",
            "Nikita is strong in close-range SMG fights. Pick her if you love quick, aggressive play with weapons like the MP40 or UMP."
        )
    )

    private val pets = listOf(
        FfItem(
            "falco", "FALCO", R.drawable.ff_pet_falco,
            "Skill: Skyline Spree",
            "Increases diving speed after jumping from the plane and gliding speed after the parachute opens.",
            "Falco helps you land first and grab the best loot before anyone else reaches the area."
        ),
        FfItem(
            "detective_panda", "DETECTIVE PANDA", R.drawable.ff_pet_detective_panda,
            "Skill: Panda's Blessings",
            "Restores HP every time you knock down an enemy.",
            "A great pet for aggressive players who chain fights back to back and need HP between them."
        ),
        FfItem(
            "ottero", "OTTERO", R.drawable.ff_pet_ottero,
            "Skill: Double Blubber",
            "Restores EP in addition to HP whenever you use a treatment item.",
            "Ottero pairs well with characters that convert EP into HP, giving you extra healing over a match."
        ),
        FfItem(
            "beaston", "BEASTON", R.drawable.ff_pet_beaston,
            "Skill: Helping Hand",
            "Increases the throwing distance of grenades, smoke grenades, flashbangs and Gloo Wall grenades.",
            "Beaston is perfect for players who love utility. Reach enemies and cover further than usual."
        ),
        FfItem(
            "rockie", "ROCKIE", R.drawable.ff_pet_rockie,
            "Skill: Stay Chill",
            "Reduces the cooldown time of your equipped active character skill.",
            "Rockie lets you use active skills like Alok or Chrono more often, which adds up across a full match."
        ),
        FfItem(
            "mr_waggor", "MR. WAGGOR", R.drawable.ff_pet_mr_waggor,
            "Skill: Smooth Gloo",
            "Produces a Gloo Wall grenade over time when you are running low on them.",
            "Mr. Waggor makes sure you never run out of cover in the late game."
        ),
        FfItem(
            "spirit_fox", "SPIRIT FOX", R.drawable.ff_pet_spirit_fox,
            "Skill: Well Fed",
            "Restores extra HP every time you use a medkit.",
            "Spirit Fox is a simple and reliable pet that makes every heal a little stronger."
        ),
        FfItem(
            "dreki", "DREKI", R.drawable.ff_pet_dreki,
            "Skill: Dragon Glare",
            "Detects nearby enemies who are using medkits and reveals their position.",
            "Dreki is great for punishing opponents who try to heal behind cover."
        ),
        FfItem(
            "flash", "FLASH", R.drawable.ff_pet_flash,
            "Companion Pet",
            "A laid-back turtle with headphones that follows you into every match.",
            "Flash is a fan favourite for style. Pair its skill with your character combination to build a setup that suits your play style."
        ),
        FfItem(
            "agent_hop", "AGENT HOP", R.drawable.ff_pet_agent_hop,
            "Skill: Bouncing Bunny",
            "Restores EP every time the safe zone shrinks.",
            "Agent Hop gives steady EP during long matches, especially useful for zone-rotation play."
        ),
        FfItem(
            "dr_beanie", "DR. BEANIE", R.drawable.ff_pet_dr_beanie,
            "Skill: Dashy Duckwalk",
            "Increases movement speed while crouching.",
            "Dr. Beanie helps you stay low and move quickly while peeking from cover."
        ),
        FfItem(
            "shiba", "SHIBA", R.drawable.ff_pet_shiba,
            "Skill: Mushroom Sense",
            "Reveals the location of nearby supply items.",
            "Shiba is great for finding healing and supplies quickly after landing."
        ),
        FfItem(
            "moony", "MOONY", R.drawable.ff_pet_moony,
            "Skill: Calming Aura",
            "Reduces the damage you take while using healing items.",
            "Moony makes it safer to heal in the middle of a fight."
        ),
        FfItem(
            "finn", "FINN", R.drawable.ff_pet_finn,
            "Companion Pet",
            "A surfing shark that brings beach vibes to the battleground.",
            "Finn is a stylish companion. Combine it with the right character skills for your favourite strategy."
        ),
        FfItem(
            "zasil", "ZASIL", R.drawable.ff_pet_zasil,
            "Companion Pet",
            "A cute axolotl in a float ring that follows you everywhere.",
            "Zasil is one of the most adorable pets in the game and a great pick for collectors."
        )
    )

    private fun bundle(id: String, name: String, image: Int, look: String, tip: String) = FfItem(
        id, name, image, "Rarity: Bundle", look, tip
    )

    private val bundles = listOf(
        bundle("hip_hop", "HIP HOP BUNDLE", R.drawable.ff_bundle_hip_hop,
            "A street-style varsity jacket with matching cap and sneakers.",
            "One of the classic bundles of the game. It still looks great with almost any gun skin."),
        bundle("red_criminal", "RED CRIMINAL BUNDLE", R.drawable.ff_bundle_red_criminal,
            "A red jumpsuit with a clown mask and hood.",
            "A rare and iconic bundle that every collector wants in their vault."),
        bundle("green_criminal", "GREEN CRIMINAL BUNDLE", R.drawable.ff_bundle_green_criminal,
            "The green version of the famous criminal jumpsuit and mask.",
            "Pairs perfectly with green weapon skins for a full matching look."),
        bundle("pajamas", "PAJAMAS BUNDLE", R.drawable.ff_bundle_pajamas,
            "Striped light-blue pajamas for a relaxed, funny look.",
            "A fun bundle to show off in the lobby and in custom rooms."),
        bundle("airspeed", "AIRSPEED BUNDLE", R.drawable.ff_bundle_airspeed,
            "A warrior outfit with a red face mask and flowing robes.",
            "A bold style that stands out in every squad photo."),
        bundle("rapper", "RAPPER BUNDLE", R.drawable.ff_bundle_rapper,
            "A teal bomber jacket with a cap and casual pants.",
            "A clean street look that suits any character."),
        bundle("lush_clubber", "LUSH CLUBBER BUNDLE", R.drawable.ff_bundle_lush_clubber,
            "A green and black party jacket with sunglasses.",
            "Great for players who like a stylish nightclub look."),
        bundle("cobra_rage", "COBRA RAGE BUNDLE", R.drawable.ff_bundle_cobra_rage,
            "A cyber suit with a red mask and cobra details.",
            "Part of the popular Cobra collection. Matches the Cobra weapon skins."),
        bundle("mood_booster", "MOOD BOOSTER BUNDLE", R.drawable.ff_bundle_mood_booster,
            "A golden crown with a snake-print jacket and gold chains.",
            "A royal, flashy outfit made to get noticed."),
        bundle("enharmonic_treble", "ENHARMONIC TREBLE BUNDLE", R.drawable.ff_bundle_enharmonic_treble,
            "A pink and red music-themed coat with neon accents.",
            "A premium event bundle with animated effects."),
        bundle("imperial_maiden", "IMPERIAL MAIDEN BUNDLE", R.drawable.ff_bundle_imperial_maiden,
            "An elegant royal dress with a veil and ornate details.",
            "One of the most graceful female bundles in the collection."),
        bundle("shadow_serpent", "SHADOW SERPENT BUNDLE", R.drawable.ff_bundle_shadow_serpent,
            "Golden-green armor with serpent scales and a dragon mask.",
            "A fierce warrior look, ideal for aggressive players."),
        bundle("valiant_shadow", "VALIANT SHADOW BUNDLE", R.drawable.ff_bundle_valiant_shadow,
            "A purple and black ninja outfit with a fur collar.",
            "A sleek stealth style that looks great in night maps.")
    )

    private fun weapon(id: String, name: String, image: Int, type: String, stats: String, tip: String) = FfItem(
        id, name, image, "Type: $type", stats, tip
    )

    private val weapons = listOf(
        weapon("m1014", "M1014", R.drawable.ff_weapon_m1014, "Shotgun",
            "Damage: Very High\nRate of fire: Low\nRange: Short\nMagazine: 6",
            "A powerful close-range shotgun. Great for clearing buildings and fighting in tight spaces."),
        weapon("mp40", "MP40", R.drawable.ff_weapon_mp40, "SMG",
            "Damage: Medium\nRate of fire: Very High\nRange: Short\nMagazine: 20",
            "One of the fastest-firing SMGs. Deadly up close when you aim for the upper body."),
        weapon("ak", "AK", R.drawable.ff_weapon_ak, "Assault Rifle",
            "Damage: High\nRate of fire: Medium\nRange: Long\nMagazine: 30",
            "High damage with noticeable recoil. Fire in short bursts at medium and long range."),
        weapon("scar", "SCAR", R.drawable.ff_weapon_scar, "Assault Rifle",
            "Damage: Medium\nRate of fire: Medium\nRange: Medium\nMagazine: 30",
            "A stable and easy-to-control rifle. A great all-round choice for beginners."),
        weapon("m1887", "M1887", R.drawable.ff_weapon_m1887, "Shotgun",
            "Damage: Extreme\nRate of fire: Low\nRange: Short\nMagazine: 2",
            "Two devastating shots per reload. Perfect for one-tap close fights."),
        weapon("groza", "GROZA", R.drawable.ff_weapon_groza, "Assault Rifle",
            "Damage: High\nRate of fire: Medium\nRange: Long\nMagazine: 30",
            "An airdrop-quality rifle with strong armor penetration. Excellent in mid-range fights."),
        weapon("awm", "AWM", R.drawable.ff_weapon_awm, "Sniper Rifle",
            "Damage: Extreme\nRate of fire: Very Low\nRange: Very Long\nMagazine: 5",
            "The strongest sniper rifle. A headshot can knock an enemy instantly."),
        weapon("m60", "M60", R.drawable.ff_weapon_m60, "LMG",
            "Damage: Medium\nRate of fire: High\nRange: Medium\nMagazine: 60",
            "A large magazine makes it great for breaking Gloo Walls and spraying squads."),
        weapon("ump", "UMP", R.drawable.ff_weapon_ump, "SMG",
            "Damage: Medium\nRate of fire: High\nRange: Short\nMagazine: 30",
            "Good armor penetration for an SMG. Reliable in close and short-medium range."),
        weapon("vector", "VECTOR", R.drawable.ff_weapon_vector, "SMG",
            "Damage: Medium\nRate of fire: Very High\nRange: Short\nMagazine: 30",
            "Extremely fast fire rate. Use it to shred enemies at close range."),
        weapon("famas", "FAMAS", R.drawable.ff_weapon_famas, "Assault Rifle",
            "Damage: Medium\nRate of fire: Burst\nRange: Medium\nMagazine: 30",
            "Fires in three-round bursts. Accurate and deadly when all bullets land."),
        weapon("m82b", "M82B", R.drawable.ff_weapon_m82b, "Sniper Rifle",
            "Damage: Very High\nRate of fire: Very Low\nRange: Very Long\nMagazine: 8",
            "Its shots go through Gloo Walls, making it perfect for punishing campers."),
        weapon("spas12", "SPAS12", R.drawable.ff_weapon_spas12, "Shotgun",
            "Damage: High\nRate of fire: Low\nRange: Short\nMagazine: 5",
            "A balanced shotgun that is easy to use in close fights."),
        weapon("g36", "G36", R.drawable.ff_weapon_g36, "Assault Rifle",
            "Damage: Medium\nRate of fire: Medium\nRange: Medium\nMagazine: 30",
            "Low recoil and good accuracy. A solid rifle for steady players."),
        weapon("katana", "KATANA", R.drawable.ff_weapon_katana, "Melee",
            "Damage: High\nAttack speed: Medium\nRange: Melee\nSpecial: Blocks bullets while swinging",
            "The katana can deflect bullets during its swing. Great for surprise close-range kills.")
    )

    private fun vehicle(id: String, name: String, image: Int, seats: Int, info: String, tip: String) = FfItem(
        id, name, image, "Seats: $seats", info, tip
    )

    private val vehicles = listOf(
        vehicle("sports_car", "SPORTS CAR", R.drawable.ff_vehicle_sports_car, 2,
            "The fastest four-wheel vehicle on the map, with low durability.",
            "Use it to rotate quickly across open roads, but avoid long fights inside it."),
        vehicle("jeep", "JEEP", R.drawable.ff_vehicle_jeep, 4,
            "A sturdy off-road vehicle with good speed and durability.",
            "A reliable choice for full squads moving to the next safe zone."),
        vehicle("pickup_truck", "PICKUP TRUCK", R.drawable.ff_vehicle_pickup_truck, 4,
            "A tough truck with high durability and medium speed.",
            "Its high health makes it great for late-game rotations under fire."),
        vehicle("motorcycle", "MOTORCYCLE", R.drawable.ff_vehicle_motorcycle, 2,
            "A quick and agile bike that can turn sharply.",
            "Perfect for solo players who need to cross the map fast."),
        vehicle("amphibious_car", "AMPHIBIOUS CAR", R.drawable.ff_vehicle_amphibious_car, 4,
            "Drives on land and floats on water.",
            "Use it to cross rivers and reach islands that other vehicles cannot."),
        vehicle("tuk_tuk", "TUK TUK", R.drawable.ff_vehicle_tuk_tuk, 3,
            "A small three-wheeler that is easy to control.",
            "Fun and handy for short rotations between nearby buildings."),
        vehicle("monster_truck", "MONSTER TRUCK", R.drawable.ff_vehicle_monster_truck, 4,
            "Huge wheels let it climb over rough terrain and obstacles.",
            "Great on hills and uneven ground where other vehicles get stuck.")
    )

    private val emoteNames = listOf(
        "HELLO", "BALLERINA", "VICTORY POSE", "FACEPALM", "SHOOT DANCE", "FLEX", "JUMP KICK", "ZOMBIE WALK",
        "CLAP CLAP", "KNEEL ROAR", "ARMS WIDE", "PARTY DANCE", "PROPOSAL", "DAB", "BOOYAH", "THUMBS UP"
    )
    private val emoteImages = intArrayOf(
        R.drawable.ff_emote_1, R.drawable.ff_emote_2, R.drawable.ff_emote_3, R.drawable.ff_emote_4,
        R.drawable.ff_emote_5, R.drawable.ff_emote_6, R.drawable.ff_emote_7, R.drawable.ff_emote_8,
        R.drawable.ff_emote_9, R.drawable.ff_emote_10, R.drawable.ff_emote_11, R.drawable.ff_emote_12,
        R.drawable.ff_emote_13, R.drawable.ff_emote_14, R.drawable.ff_emote_15, R.drawable.ff_emote_16
    )

    private val emotes = emoteNames.mapIndexed { index, name ->
        FfItem(
            "emote_${index + 1}", name, emoteImages[index],
            "Emote",
            "Show off in the lobby or celebrate after a Booyah with the $name emote.",
            "Emotes are cosmetic only and do not affect gameplay. Use them to celebrate with your squad."
        )
    }

    private fun parachute(id: String, name: String, image: Int, info: String) = FfItem(
        id, name, image, "Parachute skin", info,
        "Parachute skins are cosmetic and change how you look while dropping onto the map."
    )

    private val parachutes = listOf(
        parachute("classic", "CLASSIC PARACHUTE", R.drawable.ff_parachute_classic, "The default parachute every survivor starts with."),
        parachute("dragon", "DRAGON PARACHUTE", R.drawable.ff_parachute_dragon, "A fiery canopy with a dragon pattern."),
        parachute("rainbow", "RAINBOW PARACHUTE", R.drawable.ff_parachute_rainbow, "Bright rainbow stripes that stand out in the sky."),
        parachute("booyah", "BOOYAH PARACHUTE", R.drawable.ff_parachute_booyah, "Celebrate every drop with the Booyah design."),
        parachute("cobra", "COBRA PARACHUTE", R.drawable.ff_parachute_cobra, "Part of the Cobra collection with snake-scale details."),
        parachute("sakura", "SAKURA PARACHUTE", R.drawable.ff_parachute_sakura, "Soft cherry-blossom colours for a calm landing."),
        parachute("galaxy", "GALAXY PARACHUTE", R.drawable.ff_parachute_galaxy, "A deep-space canopy full of stars."),
        parachute("tiger", "TIGER PARACHUTE", R.drawable.ff_parachute_tiger, "Bold tiger stripes for fearless players.")
    )

    private fun tip(n: Int, title: String, body: String) = FfItem(
        "tip_$n", title, R.drawable.img_tip_icon, title, body, ""
    )

    private val tips = listOf(
        tip(1, "Land in High-Loot Areas",
            "Drop in locations with high loot density to secure better weapons and armor early. This increases survival chances and gives you an early combat advantage."),
        tip(2, "Master Headshot Aim",
            "Keep your crosshair at head level while moving and drag slightly upward when firing. Headshots deal far more damage and finish fights much faster."),
        tip(3, "Use Gloo Walls Smartly",
            "Throw a Gloo Wall the moment you are caught in the open, and use it to block enemy fire while healing or reviving. Quick cover often decides the fight."),
        tip(4, "Control Recoil Properly",
            "Fire in short bursts at medium and long range and pull down gently to keep bullets on target. Attachments like a muzzle or foregrip make recoil easier to manage."),
        tip(5, "Always Keep Moving",
            "A moving target is harder to hit. Strafe left and right, crouch and jump during fights so enemies cannot land clean shots on you."),
        tip(6, "Choose the Right Character Skills",
            "Combine one active skill with passive skills that support your play style. Aggressive players benefit from speed and damage skills, while supporters should pick healing skills."),
        tip(7, "Use Headphones",
            "Footsteps, gunshots and vehicle sounds reveal where enemies are. Good headphones help you react before the enemy even sees you."),
        tip(8, "Manage Inventory Wisely",
            "Carry enough ammo, medkits and Gloo Walls, and drop items you will not use. A clean inventory lets you pick up important loot quickly."),
        tip(9, "Know the Safe Zone Timing",
            "Watch the zone timer and rotate early. Moving late forces you to run through open ground where you are an easy target."),
        tip(10, "Switch Weapons Strategically",
            "Pair a close-range weapon with a long-range one and switch based on distance. Switching is often faster than reloading in the middle of a fight."),
        tip(11, "Play with Squad Roles",
            "Give each teammate a role such as rusher, sniper or supporter. A squad with clear roles moves and fights much more effectively."),
        tip(12, "Use High Ground Advantage",
            "Fight from rooftops, hills and upper floors whenever possible. High ground gives better visibility and makes you harder to hit."),
        tip(13, "Avoid Unnecessary Fights",
            "Not every fight is worth taking. Skip risky battles in the open and save your health and resources for the final circles."),
        tip(14, "Revive Safely",
            "Before reviving a teammate, place a Gloo Wall or smoke grenade for cover. Reviving in the open usually gets both players knocked."),
        tip(15, "Practice in Training Mode",
            "Spend a few minutes in Training Ground before matches to warm up your aim, test sensitivity and try new weapons.")
    )
}
