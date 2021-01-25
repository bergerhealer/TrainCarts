# TrainCarts
[Spigot Resource Page](https://www.spigotmc.org/resources/traincarts.39592/) | [Dev Builds](https://ci.mg-dev.eu/job/TrainCarts/) | [Javadocs](https://ci.mg-dev.eu/javadocs/TrainCarts/)

This Minecraft Bukkit server plugin links carts together to form trains you can control!

It looks for suitable Minecarts and links them together if possible. When two Minecarts are being "linked", the Minecarts will act as one single moving train. 
Once carts are successfully linked, an effect is played and their velocity is shared in combination with an individual factor for each Minecart, which is used to remain a steady gap between carts. This gap is adjustable, the force at which this happens as well.

End result: a train! You can move it, make a roller-coaster out of it, split it in half, watch trains collide or whatever you want to do with trains. :)

## Development
1. Use JDK 1.8 & Maven
2. Download and run [BuildsTools](https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar)
3. Fork or clone this project locally
4. Start developing!