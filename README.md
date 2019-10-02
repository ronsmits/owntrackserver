# owntrackserver
http gateway from owntrack to domoticz

[Domoticz](http://domoticz.com) is a Home automation application, [Owntrack](http://owntracks.org) is a location tracker
that reports to a server / system of your own without sending it to the cloud (like Google, Apple or Facebook).

When Domoticz is setup with an Mqtt broker it will publish all action on the topic `domoticz/out`
and it will listen on the topic `domoticz/in`. Owntracks on Android or IOS can be setup to report the location either using Mqtt or Http.
there is a [Owntrack server](https://github.com/owntracks/recorder) that works very well, but in my humble opinion does too much for what I want to use it for
. This little server will listen as a REST endpoint server on port 7000 and publish location updates to an mqtt broker.

### setup
This server is **not** meant to be connected directly to the internet. It should sit behind a server like Nginx or Apache http
that is setup with a domain name and a correctly setup SSL certificate. Use [Let's encrypt](https://letsencrypt.org) for the certificate.

Example of the Mqtt message:
```json
{"command":"switchlight","idx":23,"switchcmd":"On"}
```

Idx should be linked to a virtual switch in domoticz. 
The location reporting is based on the regions of Opentrack. Define a region called **home**. If the opentrack 
location contains the ```inregions=[home]```, the switchcmd will be "On" otherwise it will be "Off".

The server can support multiple users. They are defined in a json file:
```json
[
  {
    "user": "ron",
    "password": "roVAGbu4tTvR2",
    "deviceId": 23,
    "roles": [
      "REPORT"
    ]
  },
  {
    "user": "test",
    "password": "passs",
    "deviceId": 1,
    "roles": [
      "REPORT"
    ]
  }
]
```
The deviceId must be used as `tid` in the opentracks android or IOS application.
The password is a UnixCrypted using the 'username' as salt. To make it easy to create it
there is a utility class in the jar that takes the username and password. It can be called as:

```java -cp owntracks.jar CryptorKt username password```

The `roles` array must contain the role "REPORT" if you want that user to be able to report to this server
using the Owntrack app.

The server is started with :

```java -jar owntracks.jar -f users.json -m tcp://domoticz.home:1883 -c contextroot```

\-f should point to the json file defining the users defaults to `users.json`

\-m should point to your mqtt broker defaults to `tcp://127.0.0.1:1883`

\-c optionally change the contextroot from the default `"/"` 

## Credits
The distance function in Location is based on the haversine implementation from [jferrao](https://gist.github.com/jferrao/cb44d09da234698a7feee68ca895f491)