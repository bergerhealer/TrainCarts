<!-- rewrite once spigot "[⤓ Modrinth](https://modrinth.com/plugin/traincarts) / [⤓ Spigot](https://www.spigotmc.org/resources/traincarts.39592/) / " "" -->
〚 [⤓ Modrinth](https://modrinth.com/plugin/traincarts) / [⤓ Spigot](https://www.spigotmc.org/resources/traincarts.39592/) / [⤓ Jenkins Dev Builds](https://ci.mg-dev.eu/job/TrainCarts/) / [Source on GitHub](https://github.com/bergerhealer/TrainCarts/tree/master/src) / [Javadocs](https://ci.mg-dev.eu/javadocs/TrainCarts/) / [Discord](https://discord.gg/wvU2rFgSnw) 〛

## 关于 TrainCarts
Minecraft 中的火车！自动化地铁网络、过山车、游船、滑雪索道或游乐园游乐设施。TrainCarts 支持自定义模型、动画、通过告示牌进行自动化以及一个支持更多插件的庞大 API！


<!-- rewrite modrinth https://www.spigotmc.org/resources/bkcommonlib.39590/ https://modrinth.com/plugin/bkcommonlib -->
### **此插件依赖 [BKCommonLib](https://www.spigotmc.org/resources/bkcommonlib.39590/)**

#### **加入Discord服务器获取帮助：[https://discord.gg/wvU2rFgSnw](https://discord.gg/wvU2rFgSnw)**

##### 请勿使用非常旧的 Minecraft 版本。TrainCarts 和 BKCommonLib 的最新版本会向下兼容较旧的 Minecraft 版本。

## [维基](https://wiki.traincarts.net/p/TrainCarts/zh)
查看 [https://wiki.traincarts.net/p/TrainCarts](https://wiki.traincarts.net/p/TrainCarts/zh) 了解插件用法

## 自定义列车
使用[附件编辑器](https://wiki.traincarts.net/p/TrainCarts/Attachments/zh)，您可以自定义列车的外观。在 Blockbench 中创建自定义车厢模型，并在游戏中乘坐它们。通过将模型的多个部分作为单独的（Armorstand）物品模型添加，可以实现大型模型。TrainCarts 将为您处理动画，并将各部分作为一个整体进行移动。

开始前建议阅读：[1.9 及更高版本中的自定义物品模型](https://www.spigotmc.org/wiki/custom-item-models-in-1-9-and-up/)

[<img width="560" height="315" src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/vid_thumb/models.jpg">](https://www.youtube.com/watch?v=dkgwe-lCW-Q "Click to view video on YouTube")

#### 游戏内地图编辑器
<img src="https://wiki.traincarts.net/images/thumb/f/f9/Attachment_editor_wooden_car.png/800px-Attachment_editor_wooden_car.png" width="50%"/>

#### 动画
对于定制乘坐体验的爱好者来说，列车可以完全动画化。这意味着您可以拥有开门和关门、旋转的推车，甚至是主题公园的游乐设施，例如：摩天轮、旋转木马和旋转木马！

[<img width="560" height="315" src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/vid_thumb/animations.jpg">](https://www.youtube.com/watch?v=8Tlc5PO7VzE "Click to view video on YouTube")

#### 演示资源包
<img src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/train_banner.png"/>

[下载](https://packs.traincarts.net/)

TrainCarts 有一个演示资源包供你下载，内含一些火车和过山车货车的模型。你可以直接使用这些模型进行操作，无需亲自使用 Blockbench。

机车和车厢是由 Nullblox 制作的。Maxi 制作了不同颜色的过山车车厢。

您可以从这里下载 zip 文件，或使用网页上显示的 server.properties 配置文件：[packs.traincarts.net](https://packs.traincarts.net/)

<details>
<summary>使用说明</summary>

#### 生成列车
新模型将与金镐的耐久度值绑定。您可以使用以下方式生成这些列车：
* [生成告示牌](https://wiki.traincarts.net/p/TrainCarts/Signs/Spawner/zh)
* [列车生成箱物品](https://wiki.traincarts.net/p/TrainCarts/Train_Spawn_Chest/zh): <code>/train chest [name]</code>
* 在附件编辑器中创建它。您可以在 <code>/train model search</code> 中找到可使用的列车。

#### 以下列车名称可以生成
* loco - 生成无动画的机车
* carr / carg / carb - 生成无动画的红/绿/蓝车厢
* loco_anim - 生成有动画轮子和活塞的机车
* carr_anim / carg_anim / carb_anim - 生成有动画轮子的红/绿/蓝车厢
* ferris - 动态摩天轮，使用动画 'spin' 控制它（警告：非常卡顿）
* spinner - 带有 3 个可控动画（'spin'、'turn' 和 'arm'）的旋转游乐设施
* rolr / rolg / rolb - 带有 4 个座位的过山车车厢
***
</details>

<details>
<summary>已保存的列车配置</summary>

#### 提取
assets/traincarts/saved_train_properties/tcdemo.yml 中的已保存列车配置存储在 zip 压缩包文件中。
当资源包在 _server.properties_ 中配置时，_TrainCarts_ 会自动检测此文件，并将文件中的列车添加到 _/savedtrain list_ 中。

若此机制由于某种原因无法工作（如您正在合并资源包）
1. 请自行提取 tcdemo.yml 并将其放置在 <code>plugins/Train_Carts/savedTrainModules/tcdemo.yml</code> 处。
2. 执行 <code>/train globalconfig reload --savedtrainproperties</code>

通常您无需这样做。
</details>

## 物理引擎
列车不仅遵循线路或受限于 Minecraft 自身的矿车物理，它们通过实时重力物理、轨道转换和能追踪轨道的轮子进行模拟。

[<img width="560" height="315" src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/vid_thumb/coasters.jpg">](https://www.youtube.com/watch?v=XfCjDgMWogU "Click to view video on YouTube")

#### 新的速度限制
可将列车设置超过一般的 0.4方块/刻 的速度来更快移动。哇！默认（可配置）的最大速度限制是每刻 5 个方块（300 公里每小时！）。

#### 保持区块加载
可为列车设置保持区块加载。这样它们就能作为移动的区块加载器，保持自身和周围小区域加载并动画化。这样无论附近是否有玩家，它们都可以自主运行。

#### 等候
使列车在遇到另一列车或占用的[闭塞区间](https://wiki.traincarts.net/p/TrainCarts/Signs/Mutex/zh)时自动减速或完全停止。可以设置加速 和/或 减速，使列车能够逼真地停止和启动以避开障碍物。<br>
[➦维基](https://wiki.traincarts.net/p/TrainCarts/Signs/Property#Wait_Property)

#### 属性
还有许多其他属性可以改变火车的行为，而且还在不断增加。
* 倾斜、重力和摩擦：创建逼真的过山车体验，或者完全关闭它们
* 碰撞：更改是否允许怪物自动进入，或玩家与矿车碰撞时发生的情况
* 玩家进入和退出：玩家是否可以进入或离开您的火车
* 更改乘客的视距（仅限 Paper 服务器）
* 标签：标记一辆列车并通过铁路网络引导它，或在途中对其进行特殊操作

[➦维基](https://wiki.traincarts.net/p/TrainCarts/Signs/Property/zh)

## 告示牌操作
沿轨道放置的告示牌用于使列车执行操作。更改列车属性、发射列车、创建车站或自动切换道岔。通过可扩展的 API，万事皆有可能。<br>
[➦维基](https://wiki.traincarts.net/p/TrainCarts/Signs/zh)

<img src="https://wiki.traincarts.net/images/4/4d/TrainCarts_signs_intro_image.png"/>

## 路径查找
让您的铁路网络在您的 Minecraft 世界中活跃起来，让列车自动前往各个目的地。<br>
[➦维基](https://wiki.traincarts.net/p/TrainCarts/PathFinding/zh)

<img src="https://raw.githubusercontent.com/bergerhealer/TrainCarts/master/misc/intersection.png" width="60%"/>

## 命令
命令可用于完成所有通过告示牌操作可以完成的事情，甚至更多。<br>
[➦维基](https://wiki.traincarts.net/p/TrainCarts/Commands/zh)

#### 选择器支持
使用 @ptrain 选择器将列车的玩家乘客作为其他（非 TrainCarts）命令的参数。或者使用 @train 一次性对多辆列车执行操作。列车可以通过立方体、距离、设置属性等多种方式进行选择。<br>
[➦维基](https://wiki.traincarts.net/p/TrainCarts/Commands/Selectors)

#### Featured
<img src="https://raw.githubusercontent.com/bergerhealer/BKCommonLib/master/misc/cloud_banner.png"/>

此插件使用 [Cloud Command Framework](https://github.com/incendo/cloud) 处理命令

若您对 TrainCarts 的命令自动补全、建议和帮助菜单的质量印象深刻，并希望将这些功能集成到您的插件中，请查看 Incendo 的 Cloud！

## 扩展
TrainCarts 提供了一个 API 支持第三方扩展插件。主要添加了对新轨道类型、新附件类型和自定义动作告示牌的支持。
* **[TC-Hangrail](https://www.spigotmc.org/resources/tc-hangrail.39627/)**: 列车可在铁栅栏下方悬浮，也可在其他类型方块下方/上方悬浮 _(由 TeamBergerhealer 开发)_
* **[TC-Coasters](https://www.spigotmc.org/resources/tc-coasters.59583/)**: 无需支撑即可在空中铺设铁轨 _(由 TeamBergerhealer 开发)_
* **[SmoothCoasters](https://www.curseforge.com/minecraft/mc-mods/smoothcoasters)**: Minecraft 客户端 Fabric 模组，安装后可平滑盔甲架动画并改善第一人称摄像机视图
* **[TCTicketShop](https://www.spigotmc.org/resources/tcticketshop.64627/)**: 使用告示牌交互购买车票 _(由 DefinitlyEvil 开发)_
* **[LightAPI](https://www.spigotmc.org/resources/lightapi.4510/)**: 为内置灯光附件提供支持。（对于 Minecraft 1.17.1 及以下版本请使用此[分支](https://www.spigotmc.org/resources/lightapi-fork.48247/)）
* **[TCJukebox](https://www.spigotmc.org/resources/tcjukebox.75674/)**: 使用 [MCJukebox](https://www.spigotmc.org/resources/mcjukebox.16024/) _（由 melerpe 开发）_ 为火车内的玩家播放音乐  
<!-- rewrite once spigot "https://modrinth.com/plugin/tc-portals" "https://www.spigotmc.org/resources/tc-portals.107442/" -->
* **[TC-Portals](https://modrinth.com/plugin/tc-portals)**: 以逼真的方式在服务器之间传送火车 _（由 J0schlZ 开发）_ Teleport trains between servers in a realistic way _(by J0schlZ)_
<!-- rewrite once spigot "https://modrinth.com/plugin/tc-destinations" "https://www.spigotmc.org/resources/tc-destinations.107441/" -->
* **[TC-Destinations](https://modrinth.com/plugin/tc-destinations)**: 跨服务器的列车目的地分页和有用的点击命令 _（由 J0schlZ 开发）_
* **[TrainCartsDestinationSelector](https://www.spigotmc.org/resources/traincartsdestinationselector.73170/)**: 添加可点击的告示牌，以便玩家选择列车目的地
* **[Traincarts AdvancedSigns](https://www.spigotmc.org/resources/traincarts-advancedsigns.99881/)**: 添加了额外的告示牌类型，特别适用于过山车
* **[TCAnimatronics](https://www.spigotmc.org/resources/tcanimatronics.101995/)**: 使用 TrainCarts 告示牌播放 [Animatronics](https://www.spigotmc.org/resources/animatronics-animate-armorstands-1-8-1-18-2.36518/) 动画
* 
## FAQ
<details>
<summary>当一些玩家乘坐火车时，列车会故障，玩家的车厢会卡在位置上</summary>

**NoCheatPlus** 认为玩家在作弊，因为矿车以非预期的方式移动。矿车被传送回一个有效的状态，导致这些奇怪的故障。要修复此问题，请在 NoCheatPlus 配置中将以下内容设置为 false：
```yaml
checks:
  moving:
    vehicle:
        enforcelocation: false
```
</details>
<details>
<summary>在 Minecraft 1.16 中玩家可以通过持续按住 Shift 键离开 playerexit=false 的列车</summary>

遗憾的是，该版本的 Minecraft 客户端存在漏洞，导致服务器无法取消玩家退出载具。更新到 Minecraft 1.17+ 将会修复此问题。另见：[SPIGOT-5891](https://hub.spigotmc.org/jira/browse/SPIGOT-5891)
</details>
<details>
<summary>当玩家靠近时列车才能到达目的地。没有人靠近时不行。</summary>

启用列车的 **保持区块加载** 属性。
</details>
<details>
<summary>我设置了一个自动生成器，但列车在其附近不断堆积</summary>

启用列车的 **保持区块加载** 属性。否则列车会在周围没有玩家时一直被卸载。
</details>
<details>
<summary>我下车后列车会消失</summary>

如果您在服务器上安装了 CraftBook，是这个插件导致了这个问题。在 [CraftBook Configuration](http://wiki.sk89q.com/wiki/CraftBook/Minecart_Exit_Remover) 配置中关闭它。
</details>

## 捐赠
如果您真的很喜欢我的作品并想回报我，欢迎通过下面的按钮通过 PayPal 捐赠一些小东西给我。谢谢！:)

[<img src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif"/>](https://www.paypal.me/teambergerhealer)
