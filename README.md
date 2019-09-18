# owntrackserver
http gateway from owntrack to domoticz

When Domoticz is setup with an Mqtt broker it will publish all action on the topic `domoticz/out`
and it will listen on the topic `domoticz/in`. This little server will listen as a REST endpoint server on port 7000
and publish location updates to mqtt broker.
Example of the Mqtt message:
```json
{"command":"switchlight","idx":23,"switchcmd":"On"}
```

Idx should be linked to a virtual switch in domoticz. 

The server can support multiple users. They are defined in a json file:
```json
[
  {
    "user": "ron",
    "password": "roVAGbu4tTvR2",
    "deviceId": 23,
    "roles": [
      "YES"
    ]
  },
  {
    "user": "test",
    "password": "passs",
    "deviceId": 1,
    "roles": [
      "YES"
    ]
  }
]
```
The deviceId must be used as `tid` in the opentracks android or IOS application.
The password is a UnixCrypted using the 'username' as salt. To make it easy to create it
there is a utility class in the jar that takes the username and password. It can be called as

```java -cp owntracks.jar CryptorKt username password```

The server is started with :

```java -jar owntracks.jar -f users.json -m tcp://domoticz.home:1883```

\-f should point to the json file defining the users

\-m should point to your mqtt broker

