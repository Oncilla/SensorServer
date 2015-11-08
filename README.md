# Sensor Server

Sensor Server transforms your Android device in a RESTful server, which provides access to the sensors and actuators of your device.</br>In addition, Websockets were introduced to provide frequently updated values.

</br>
following resources are available:
<ul>
<li>List of sensors (ip:port/sensors)</li>
<li>The current value of a specific sensor (ip:port/sensors/SENSOR_ID)</li>
<li>Vibration (ip:port/vibration?pattern=PATTERN)</li>
<li>Start sound (ip:port/sound?loop=false)</li>
<li>Start looping sound (ip:port/sound?loop=true)</li>
<li>Stop sound (ip:port/sound?loop=trues)</li>
</ul>

</br>
Additionally:
<ul>
<li>Page with websockets to a specific sensor (ip:port/sensors-websocket/SENSOR_ID)</li>
<li>Page to start sounds (ip:port/sound)</li>
<li>Page to start vibration (ip:port/vibration)</li>
</ul>

<div>

<img src="https://github.com/Oncilla/SensorServer/blob/master/device.png?raw=true" width="120">

<img src="https://github.com/Oncilla/SensorServer/blob/master/browser.png?raw=true" width="480">

</div>
