# Minecraft MultiMC-pack updater

Simple minecraft mod-pack updater for multimc by fetch mod data from server and compare changes

## At client side
1. Run `./gradlew jar`
2. Output file (fat jar) is in `build/libs` directory 
3. Copy output file to `.minecraft` folder of multimc instance
4. Add pre-launch command `$INST_JAVA -jar mcupdater.jar`
5. Copy [config.json](config.json) to `.minecraft` folder and config server ip
6. Launch! 
    + will fail to launch if it can't connect to server
    + will fail to launch if url response from server can't access
    + sometime it will continue to launch (it's feature!)
7. Export multimc instance to your friend!


## At server side
Simple data response from server

> server and port can be found [config.json](config.json)
```
GET "http://${server}:${port}/config.json"
```
should return data like this
```json5
// real response should not have comment but only for describe how it work
{
  "config": { // forge config
    "mc_version": "1.18.1", // minecraft version note: NYI
    "forge_version": "39.0.79" // forge version note: need to relaunch after update
  },
  "mods": [
    {
       // mod name (can be anything that always identical to mod after updated)
      "name": "journeymap_1.18",
      // version can be version or hash or anything that different between version
      "version": "1.18.1-5.8.0beta15",
      // if file name doesn't prefix with http it will download mod via "http://${server}:${port}/mods/${filename}"
      "file_name": "journeymap-1.18.1-5.8.0beta15.jar"
    },
    {
      "name": "jei-1.18.1",
      "version": "9.2.1.99",
      // if file name is prefix with http it will download from url directly
      "file_name": "https://media.forgecdn.net/files/3650/556/jei-1.18.1-9.4.1.99.jar"
    }
  ]
}
```

## Goal
+ [ ] Update minecraft version
+ [x] Update forge version
+ [ ] Update fabric version
+ [x] Update mods
### Priority fix
+ [ ] Add tests
+ [ ] Add comments
+ [ ] Resolve mystery bug
+ [ ] Remove unnecessary library