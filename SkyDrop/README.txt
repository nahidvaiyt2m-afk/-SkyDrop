SkyDrop - Aetherion Style
=========================
Select an area, add items, and they fall from the sky at random spots inside
that area every N seconds. First player to pick them up keeps them.

BUILD
-----
1. Install JDK 25 and Maven.
2. In this folder run:   mvn clean package
3. Take target/SkyDrop-1.0.0.jar and put it in your server's plugins folder.
4. If your Paper version is different, change <paper.version> in pom.xml
   (and api-version in src/main/resources/plugin.yml).

USE (in game, OP or permission skydrop.admin)
---------------------------------------------
/skydrop wand                       get the wand (left click = pos1, right click = pos2)
/skydrop create shop 60             create zone "shop", drops every 60 seconds
/skydrop additem shop diamond 1 3   diamond, random amount 1-3
/skydrop additem shop iron_ingot 2 6
/skydrop amount shop 8              8 items per drop
/skydrop height shop 25             items start 25 blocks above the zone top
/skydrop now shop                   test drop right now
/skydrop stop shop | start shop
/skydrop list | info shop | delete shop | reload
