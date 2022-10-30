<!-- rewrite once spigot "[⤓ Modrinth](https://modrinth.com/plugin/traincarts) / [⤓ Spigot](https://www.spigotmc.org/resources/traincarts.39592/) / " "" -->
〚 [⤓ Modrinth](https://modrinth.com/plugin/traincarts) / [⤓ Spigot](https://www.spigotmc.org/resources/traincarts.39592/) / [⤓ Jenkins Dev Builds](https://ci.mg-dev.eu/job/TrainCarts/) / [Source on GitHub](https://github.com/bergerhealer/TrainCarts/tree/master/src) / [Javadocs](https://ci.mg-dev.eu/javadocs/TrainCarts/) / [Discord](https://discord.gg/wvU2rFgSnw) 〛

## About TrainCarts
Trains in Minecraft! Automated metro networks, rollercoasters, gondolas, ski-lifts or amusement park rides. TrainCarts does it all with support for custom models, animations, automation with signs and a large API that supports add-ons that can do even more!

<!-- rewrite modrinth https://www.spigotmc.org/resources/bkcommonlib.39590/ https://modrinth.com/plugin/bkcommonlib -->
### **This plugin requires [BKCommonLib](https://www.spigotmc.org/resources/bkcommonlib.39590/)**

#### **Join our discord server for help: [https://discord.gg/wvU2rFgSnw](https://discord.gg/wvU2rFgSnw)**

##### Please do not use very old versions with older versions of Minecraft. The latest release of TrainCarts and BKCommonLib are **backwards-compatible** with older Minecraft versions.

## [WIKI](https://wiki.traincarts.net/p/TrainCarts)
Discover how to use this plugin over at [https://wiki.traincarts.net/p/TrainCarts](https://wiki.traincarts.net/p/TrainCarts)

## Custom Trains
With the [Attachment Editor](https://wiki.traincarts.net/p/TrainCarts/Attachments) you can customize the appearance of your trains. Create custom cart models in Blockbench and ride inside them in-game. Large models are possible by adding multiple sections of a model as separate (Armorstand) item models. Traincarts will do the animating for you and move the parts as a whole.

Recommended reading before going into this: [Custom Item Models in 1.9 and Up](https://www.spigotmc.org/wiki/custom-item-models-in-1-9-and-up/)

[<img width="560" height="315" src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/vid_thumb/models.jpg">](https://www.youtube.com/watch?v=dkgwe-lCW-Q "Click to view video on YouTube")

#### Ingame Map Editor
<img src="https://wiki.traincarts.net/images/thumb/f/f9/Attachment_editor_wooden_car.png/800px-Attachment_editor_wooden_car.png" width="50%"/>

#### Animations
For custom ride enthusiasts, trains can be fully animated. This means you can have doors that open and close, carts that spin around, or even create theme park rides like ferris wheels, spinners and merry-go-rounds!

[<img width="560" height="315" src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/vid_thumb/animations.jpg">](https://www.youtube.com/watch?v=8Tlc5PO7VzE "Click to view video on YouTube")

#### Demo Resource Pack
<img src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/train_banner.png"/>

Traincarts has a demo resource pack that you can download that includes a couple train and rollercoaster cart models. You can already play around with these without having to delve into Blockbench yourself

The locomotive and carriages were made by Nullblox. Maxi made the different color rollercoaster carts.

<details>
<summary>Installation Instructions</summary>

#### 1. Download the resource pack
[Download it from Dropbox here](https://www.dropbox.com/s/th8ei3crc01bfx2/TrainCarts_Demo_TP_v3.zip?dl=0)

#### 2.A: Host it
Host the zip file somewhere on a FTP, your own server, a public resource pack host or some form of cloud storage that allows for a direct download. Then add the URL to your **server.properties**:<br>
<code>resource-pack=https://your-domain/path/to/TrainCarts_Demo_TP_v3.zip</code>

This is the recommended way, as this way the train models also show properly inside the attachment editor.

#### 2.B: Enable it in your client
You can follow [this tutorial](https://minecraft.fandom.com/wiki/Tutorials/Loading_a_resource_pack) on the Minecraft Wiki to install the zip file as a resource pack in your Minecraft client.

**Important note**: you will get a warning about the resource pack being outdated, but it works just fine on modern versions of Minecraft. That warning can be ignored.

#### 3. Download the train configurations
[Download a zip file with the configuration from Dropbox here](https://www.dropbox.com/s/l9uglizl6gbunxe/TrainCarts_Demo_ST_v1.zip?dl=0)

#### 4. Extract the zip file
Extract the zip file, you should now have a fairly large _tcdemo.yml_ file. This file is going to be installed in the server.

#### 5. Install the tcdemo.yml
Install this file at <code>plugins/Train_Carts/savedTrainModules/tcdemo.yml</code>

#### 6. Reload saved trains
Either restart the server, or use <code>/train globalconfig reload --savedtrainproperties</code>
***
</details>

<details>
<summary>Usage Instructions</summary>

#### Spawn the trains
The new models are bound to durability values of the golden pickaxe. After installing the _tcdemo.yml_ demo train configurations, you can spawn these trains using:
* [Spawn Sign](https://wiki.traincarts.net/p/TrainCarts/Signs/Spawner)
* [Train Spawn Chest Item](https://wiki.traincarts.net/p/TrainCarts/Train_Spawn_Chest): <code>/train chest [name]</code>

#### The following train names can be spawned
* loco - Spawns locomotive without animations
* carr / carg / carb - Spawns red/green/blue carriages without animations
* loco_anim - Spawns locomotive with animation 'wheel' to bring it to life
* carr_anim / carg_anim / carb_anim - Spawns red/green/blue carriages with animation 'wheel' to bring it to life
* ferris - Animated ferris wheel, use animation 'spin' to control it (WARNING: VERY LAGGY)
* spinner - Animated spinning ride with 3 controllable animations ('spin', 'turn' and 'arm')
* rolr / rolg / rolb - Rollercoaster carts with 4 seats
***
</details>

## Physics
Trains don't just follow a line or are limited by Minecraft's own Minecart physics. They are simulated in real-time with gravity physics, rail switching and wheels that track the rails.

[<img width="560" height="315" src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/vid_thumb/coasters.jpg">](https://www.youtube.com/watch?v=XfCjDgMWogU "Click to view video on YouTube")

#### New Speed Limits
It is possible to make trains move much faster than the usual 0.4 blocks per tick. Zoom! The default (configurable) maximum speed limit is 5 blocks per tick (300kph!).

#### Keep Chunks Loaded
Trains can be set to keep chunks loaded. Then they act as moving chunk loaders, keeping themselves and a small area around them loaded and animated. This way they can run autonomously, no matter if no players are nearby.

#### Waiter
Make trains automatically slow down or stop completely when another train or occupied [mutex zone](https://wiki.traincarts.net/p/TrainCarts/Signs/Mutex) is up ahead. An acceleration and/or deceleration can be set to make trains stop and start realistically to avoid obstacles.<br>
[➦Wiki](https://wiki.traincarts.net/p/TrainCarts/Signs/Property#Wait_Property)

#### Properties
There are lots of other properties to change the train's behavior, and more are added all the time.
* Banking, gravity and friction: create realistic rollercoaster rides, or turn it off entirely
* Collision: change whether mobs enter automatically, or what happens when a player collides with a train
* Player enter and exit: Whether players can get in or out of your train
* Change view distance of the occupants (Paper server only)
* Tags: tag a train and route it through the train network, or do special things to it along the way

[➦Wiki](https://wiki.traincarts.net/p/TrainCarts/Signs/Property)

## Sign Actions
Signs placed along the rails are used to make the trains do things. Change train properties, launch trains, create stations or automatically switch junctions. With an extendable API anything is possible.<br>
[➦Wiki](https://wiki.traincarts.net/p/TrainCarts/Signs)

<img src="https://wiki.traincarts.net/images/4/4d/TrainCarts_signs_intro_image.png"/>

## Path Finding
Make your train network come alive with trains that automatically travel to destinations all over your Minecraft world.<br>
[➦Wiki](https://wiki.traincarts.net/p/TrainCarts/PathFinding)

<img src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/intersection.png" width="60%"/>

## Commands
Everything that can be done with sign actions, and more, can also be done using commands.<br>
[➦Wiki](https://wiki.traincarts.net/p/TrainCarts/Commands)

#### Selector Support
Use the **@ptrain** selector to use the player passengers of a train as an argument in other (non-Traincarts) commands. Or use **@train** to perform actions on multiple trains at once. Trains can be selected by cuboid, distance, set properties and many more.<br>[➦Wiki](https://wiki.traincarts.net/p/TrainCarts/Commands/Selectors)

#### Featured
<img src="https://raw.githubusercontent.com/bergerhealer/BKCommonLib/master/misc/cloud_banner.png"/>

This plugin uses the [Cloud Command Framework](https://github.com/incendo/cloud) for handling commands

If you're impressed by the quality of Traincarts' command auto-completions, suggestions and help menu and want this in your plugin, check out Cloud by Incendo!

## Addons
TrainCarts exposes an API that allows third-parties to extend the plugin. Primarily it adds support for new rail types, new attachment types and custom action signs.
* **[TC-Hangrail](https://www.spigotmc.org/resources/tc-hangrail.39627/)**: Trains floating below iron fences and also below/above other kinds of blocks _(by TeamBergerhealer)_
* **[TC-Coasters](https://www.spigotmc.org/resources/tc-coasters.59583/)**: Rails in the air without requiring actual rails blocks _(by TeamBergerhealer)_
* **[SmoothCoasters](https://www.curseforge.com/minecraft/mc-mods/smoothcoasters)**: Minecraft Client Fabric mod that, if installed, smoothens armorstand animations and improves the first-person camera view
* **[TCTicketShop](https://www.spigotmc.org/resources/tcticketshop.64627/)**: Use sign interaction to buy train tickets _(by DefinitlyEvil)_
* **[LightAPI](https://www.spigotmc.org/resources/lightapi.4510/)**: Powers the built-in Light attachment. (Use this [fork](https://www.spigotmc.org/resources/lightapi-fork.48247/) for MC 1.17.1 and below)
* **[TCJukebox](https://www.spigotmc.org/resources/tcjukebox.75674/)**: Plays music for players inside the train using [MCJukebox](https://www.spigotmc.org/resources/mcjukebox.16024/) _(by melerpe)_
* **[TrainCartsDestinationSelector](https://www.spigotmc.org/resources/traincartsdestinationselector.73170/)**: Adds clickable signs so players can select train destinations
* **[Traincarts AdvancedSigns](https://www.spigotmc.org/resources/traincarts-advancedsigns.99881/)**: Adds additional sign types, particularly useful for rollercoaster rides
* **[TCAnimatronics](https://www.spigotmc.org/resources/tcanimatronics.101995/)**: Play [Animatronics](https://www.spigotmc.org/resources/animatronics-animate-armorstands-1-8-1-18-2.36518/) animations with a Traincarts sign

## Promotional
[<img src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/powerplugins_banner.png"/>](https://powerplugins.net/)

You can join the Minecraft server hosted over at **[powerplugins.net](https://powerplugins.net/)** to see this plugin in action, as well check out other awesome plugins by the community!

## FAQ
<details>
<summary>When some players ride a train, the train glitches out and the player's cart gets stuck in position</summary>

**NoCheatPlus** thinks the player is cheating, because the cart is moving in unintended ways. The cart is teleported back to a valid state causing these strange glitches. To fix this, set the following to false in the NoCheatPlus configuration:
```yaml
checks:
  moving:
    vehicle:
        enforcelocation: false
```
</details>
<details>
<summary>Im on Minecraft 1.16 and players can get out of my playerexit=false train by pressing shift</summary>

Sadly, Minecraft client had a bug in it where the server could not cancel players exiting from vehicles. Updating to Minecraft 1.17+ will fix this again. See also: [SPIGOT-5891](https://hub.spigotmc.org/jira/browse/SPIGOT-5891)
</details>
<details>
<summary>When players are near, trains reach their destinations. With no one near, they don't.</summary>

Enable the **keep chunks loaded** property of the train.
</details>
<details>
<summary>I created an auto-spawner but trains are piling up somewhere closeby</summary>

Enable the **keep chunks loaded** property of the train. Trains will otherwise unload in the same spot when no player is near.
</details>
<details>
<summary>When I exit a train, the train disappears</summary>

If you have CraftBook installed on the server, this plugin can actually do exactly that. Turn it off in the [CraftBook Configuration](http://wiki.sk89q.com/wiki/CraftBook/Minecart_Exit_Remover).
</details>

## Donate
If you really like my work and want to give something in return, feel free to donate something small to me over PayPal using the button down below. Thank you! :)

[<img src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif"/>](https://www.paypal.me/teambergerhealer)