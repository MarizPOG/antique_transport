# Antique Transport

Puts trains, tracks, stations, and airships on your [**Antique Atlas**](https://modrinth.com/mod/antique-atlas-4) map.
Requires [Create](https://modrinth.com/mod/create) and/or [Sable](https://modrinth.com/mod/sable) to do anything useful — but loads just fine without them,
bravely rendering nothing.

Available on **Fabric** and **NeoForge** (via [Sinytra Connector](https://modrinth.com/mod/connector)).

---

<p>
  <a href="https://ibb.co/rG9rpHFm">
<img src="https://i.ibb.co/bgt9vXQP/map1.png" alt="map1"></a>
</p>

## Features

- **Train tracks** — Your entire rail empire, drawn on the map. Yes, including that branch line to nowhere.
- **Live trains** — Moving markers with name and route tooltips, so you can watch your trains be late in real time.
- **Stations** — Create stations appear as named landmarks. Finally, a reason to give them proper names.
- **Ships & airships** — Sable vessels shown as directional markers with name, altitude, and last-seen time (all optional). To add a ship to the map, click on it while the atlas is open, or run `/antique_transport markship` while aboard.
- **Bookmark buttons** — Hide all train layers or all ships with one click. Useful when the map gets crowded, or when you're embarrassed by your rail network.
- **Per-feature config** — Each layer (tracks / trains / stations / ships) toggled independently in `antique_transport.toml`.


|                                       | Client only                                                                                                                          | Client + Server                                                       |
|---------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| **Train Tracks, Stations, Trains**    | ✅ Rendered across the whole map                                                                                                      | ✅ Same                                                                |
| **Ships**<br/><sub>(sub-levels)</sub> | ⚠️ Only visible within render distance; not synced with other clients. Shows last-seen time so you at least know the ship *existed*. | ✅ Synced with server and all clients, works across all loaded chunks. |

***

<p>
  <a href="https://ibb.co/4gJx47wf"><img src="https://i.ibb.co/n8RSQbq0/map2.png" alt="map2" ></a>
</p>

## Planned / TODO

- **Nameplate sync** — ship names set via in-world Nameplates from Aeronautics will automatically reflect on the map, no manual naming needed.

> **Disclaimer:** This mod was built with significant AI assistance. The code and architecture were developed in collaboration with an AI coding assistant.