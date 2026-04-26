# Antique Transport

Puts trains, tracks, stations, and airships on your [**Antique Atlas**](https://modrinth.com/mod/antique-atlas-4) map.
Works with [Create](https://modrinth.com/mod/create), [Sable](https://modrinth.com/mod/sable) and [Create Simulated](https://modrinth.com/mod/create-aeronautics).

Available on **Fabric** and **NeoForge** (via [Sinytra Connector](https://modrinth.com/mod/connector)).

---

<p>
  <a href="https://cdn.modrinth.com/data/KW2DN49D/images/50dc8a3406a9e3c186cda591ec2f82a56408adcb.png">
<img src="https://cdn.modrinth.com/data/KW2DN49D/images/50dc8a3406a9e3c186cda591ec2f82a56408adcb.png" alt="map1"></a>
</p>

## Features

- **Train tracks** - Your entire rail empire, drawn on the map. Yes, including that branch line to nowhere.
- **Live trains** - Moving markers with name and route tooltips, so you can watch your trains be late in real time.
- **Stations** - Create stations appear as named landmarks. Finally, a reason to give them proper names.
- **Ships & airships** - Sable sublevels shown as directional markers with name, altitude, and last-seen time (all optional). To add a ship to the map, click on it while the atlas is open, or run `/antique_transport markship` while aboard.
- **Automatic naming** - Ships named via Simulated **Nameplates** are automatically reflected on the map.
- **Bookmark buttons** - Hide all train layers or all ships with one click. Useful when the map gets crowded, or when you're embarrassed by your rail network.
- **Per-feature config** - Each layer (tracks / trains / stations / ships) toggled independently in `antique_transport.toml` via [McQoy](https://modrinth.com/mod/mcqoy).


|                                       | Client only                                                                                                                          | Client + Server                                                       |
|---------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| **Train Tracks, Stations, Trains**    | ✅ Rendered across the whole map                                                                                                      | ✅ Same                                                                |
| **Ships**<br/><sub>(sub-levels)</sub> | ⚠️ Only visible within render distance; not synced with other clients. Shows last-seen time so you at least know the ship *existed*. | ✅ Synced with server and all clients, works across all loaded chunks. |

***

<p>
  <a href="https://cdn.modrinth.com/data/KW2DN49D/images/85a41c24788ad37e10ac66914589f9c7c7d5190b.png">
<img src="https://cdn.modrinth.com/data/KW2DN49D/images/85a41c24788ad37e10ac66914589f9c7c7d5190b.png" alt="map2" ></a>
</p>

## Planned / TODO

- Feature suggestions welcome on [GitHub Issues](https://github.com/MarizPOG/antique_transport)!

## Credits

- [justliliandev](https://github.com/justliliandev) - original author of Create train map integration. 
> **Disclaimer:** This mod was built with significant AI assistance. The code and architecture were developed in collaboration with an AI coding assistant.