# Antique Transport

Puts trains, tracks, stations, and airships on your [**Antique Atlas**](https://modrinth.com/mod/antique-atlas-4) map.
Works with [Create](https://modrinth.com/mod/create), [Sable](https://modrinth.com/mod/sable) and [Create Simulated](https://modrinth.com/mod/create-aeronautics).

Available on **Fabric** and **NeoForge** (via [Sinytra Connector](https://modrinth.com/mod/connector)).

---

<p>
  <a href="https://cdn.modrinth.com/data/KW2DN49D/images/f2b5caee01396124996a124b3c854838bde98127.png">
<img src="https://cdn.modrinth.com/data/KW2DN49D/images/f2b5caee01396124996a124b3c854838bde98127.png" alt="map1"></a>
</p>

## Features

### Create

- **Train tracks** – Your entire rail empire, drawn on the map. Yes, including that branch line to nowhere.
- **Live trains** – Moving markers with name and route tooltips, so you can watch your trains be late in real time.
- **Stations** – Create stations appear as named landmarks. Finally, a reason to give them proper names.

### Sable

- **Ships & airships** – Sable sublevels shown as directional markers with name, altitude, and last-seen time (all optional).  
  To add a ship to the map, click on it while the atlas is open, or run `/antique_transport markship` while aboard.
- **Per-ship visibility** – Each ship can be shown or hidden individually, so your test balloons do not clutter the strategic view.
- **Bookmark buttons** – A single toggle button to show/hide all ships at once. Perfect for when the sky gets crowded.

### Create Simulated

- **Dynamic ship previews** – With Create Simulated installed, ships can be rendered directly on the atlas as live top-down diagrams instead of just markers. Tiny floating contraptions, now in convenient map form.
- **Automatic naming** – Ships named via Simulated **Nameplates** are automatically reflected on the map, so you do not have to remember which one was “Definitely Not A Safety Hazard”.
<p>
  <img src="https://cdn.modrinth.com/data/KW2DN49D/images/0c61c1cf6c10615941ebeb777a788a508cd53bcf.gif" width="49%" alt="Ships preview.
Your helicopter hovers anxiously on the map, unsure whether it's a vehicle or just a very loud fan.">
  <img src="https://cdn.modrinth.com/data/KW2DN49D/images/da09288a2af591d0cd7dbe0fbb5722472462c02d.gif" width="49%" alt="Airplane passing by.
Watch your airplane gracefully soar across the map — or crash into the ocean, we don't judge.">
</p>

### General

- **Bookmark buttons** – Separate buttons to hide all train layers or all ships with one click. Useful when the map gets crowded, or when you're embarrassed by your rail network.
- **Per-feature config** – Each layer (tracks / trains / stations / ships / previews) toggled independently in `antique_transport.toml` via [McQoy](https://modrinth.com/mod/mcqoy).

|                                                             | Client only                                                                                                            | Client + Server                                                                                                        |
|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| **Train Tracks, Stations, Trains**                          | ✅ Rendered across the whole map. Same as client + server, because trains do not believe in networking drama.           | ✅ Same as client only. Nice and boring, just how infrastructure likes it.                                              |
| **Ships**<br/><sub>(sub-levels)</sub>                       | ⚠️ Not synced with other clients. Shows last-seen time so you at least know the ship *existed*.                        | ✅ Synced with server and all clients, works across all loaded chunks.                                                  |
| **Dynamic Ship Previews**<br/><sub>(Create Simulated)</sub> | ⚠️ Live ship diagrams work for ships within render distance; outside it, the last rendered preview can still be shown. | ✅ Same behavior as client only, but with proper syncing, shared naming, and no ghost ships haunting the atlas forever. |

***

<p>
  <a href="https://cdn.modrinth.com/data/KW2DN49D/images/3d6f940a281a43a4f65eab3b3f36a374195936f5.png">
<img src="https://cdn.modrinth.com/data/KW2DN49D/images/3d6f940a281a43a4f65eab3b3f36a374195936f5.png" alt="map2" ></a>
</p>

## Planned / TODO

- Feature suggestions welcome on [GitHub Issues](https://github.com/MarizPOG/antique_transport)!

## Credits

- [justliliandev](https://github.com/justliliandev) - original author of Create train map integration.
> **Disclaimer:** This mod was built with significant AI assistance. The code and architecture were developed in collaboration with an AI coding assistant.