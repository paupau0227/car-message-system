import threading
import time
import tkinter as tk
from bluepy import btle  
import binascii  
import sys

MAC_ADDRESS = '58:3c:16:3d:6e:d8'
DEVICE_NAME = 'Pixel 6'
MY_SERVICE_UUID = '00001101-0000-1000-8000-00805F9B34FB'
MY_CHARACTERISTIC_UUID = '00002201-0000-1000-8000-00805F9B34FB'

class MyDelegate(btle.DefaultDelegate):  
    def __init__(self):
        btle.DefaultDelegate.__init__(self)  
        self.message_buffer = []  

    def handleNotification(self, cHandle, data):
        print('Received notification (raw): ', data)

        try:
            message = data.decode('utf-8')
        except UnicodeDecodeError:
            message = data.decode('utf-8', 'replace')  
        
        if message == 'end':
            complete_message = ''.join(self.message_buffer)
            print('Received complete message: ' + complete_message)
            if complete_message == 'プログラム終了':
                sys.exit()
            self._display_message(complete_message)
            self.message_buffer = []  
        else:  
            self.message_buffer.append(message)

        print('Received notification: ' + message)

    def _display_message(self, message):
        def _blink():
            for _ in range(3):  # Blink 3 times
                root = tk.Tk()  
                root.attributes('-fullscreen', True)  # Fullscreen window
                # Format message to break every 5 characters
                formatted_message = "\n".join([message[i:i+5] for i in range(0, len(message), 5)])
                label = tk.Label(root, text=formatted_message, font=("Helvetica", 200))  # Larger font size
                label.pack(expand=True)  
                root.after(1000, lambda: root.destroy())  # Close window after 1 second
                root.mainloop()  
                time.sleep(3)  # Pause between blinks
        threading.Thread(target=_blink).start()

# Start with a blank white fullscreen
root = tk.Tk()
root.attributes('-fullscreen', True)
label = tk.Label(root, text="", font=("Helvetica", 200))
label.pack(expand=True)
root.update()

scanner = btle.Scanner()
peripheral = None

while peripheral is None:
    devices = scanner.scan(1.0)
    for device in devices:
        if device.getValueText(9) == DEVICE_NAME:
            print('Found Android device with device name: ' + DEVICE_NAME)
            print('Device MAC address: ' + device.addr)
            scan_data = device.getScanData()
            for (adtype, desc, value) in scan_data:
                if desc == '16b Service Data':
                    hello_hex = value[4:]
                    hello_str = binascii.unhexlify(hello_hex).decode('utf-8')
                    print('Received hello message: ' + hello_str)
            try:
                time.sleep(3.0)
                peripheral = btle.Peripheral(device)
                break
            except btle.BTLEException as ex:
                print('Failed to connect to the device: ' + str(ex))
                continue

if peripheral is None:
    print('Could not find or connect to the device with MAC address: ' + MAC_ADDRESS)
else:
    peripheral.setDelegate(MyDelegate())

    try:
        service = peripheral.getServiceByUUID(MY_SERVICE_UUID)
        characteristic = service.getCharacteristics(MY_CHARACTERISTIC_UUID)[0]
    except btle.BTLEException as ex:
        print('Failed to get service or characteristic: ' + str(ex))

    while True:
        try:
            if peripheral.waitForNotifications(3.0):
                continue
        except btle.BTLEException as ex:
            print('Connection lost: ' + str(ex))
            break
