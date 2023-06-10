from bluepy import btle
import binascii
import time

MAC_ADDRESS = '58:3c:16:3d:6e:d8'
DEVICE_NAME = 'Pixel 6'
MY_SERVICE_UUID = '00001101-0000-1000-8000-00805F9B34FB'
MY_CHARACTERISTIC_UUID = '00002201-0000-1000-8000-00805F9B34FB'

class MyDelegate(btle.DefaultDelegate):
    def __init__(self):
        btle.DefaultDelegate.__init__(self)
        self.message_buffer = []

    def handleNotification(self, cHandle, data):
        # Print raw notification data
        print('Received notification (raw):', data)
        
        try:
            message = data.decode('utf-8')  # Decode the data as UTF-8 string
        except UnicodeDecodeError:
            message = data.decode('utf-8', 'replace')  # If decoding fails, ignore or replace with a replacement character
        
        if message == 'end':
            complete_message = ''.join(self.message_buffer)
            print('Received complete message:', complete_message)
            self.message_buffer = []
        else:
            self.message_buffer.append(message)

        print('Received notification:', message)


scanner = btle.Scanner()  # Initialize Bluetooth scanner
peripheral = None

while peripheral is None:
    devices = scanner.scan(1.0)  # Scan for nearby devices for 1 second
    for device in devices:
        if device.getValueText(9) == DEVICE_NAME:  # Check if device name matches the specified name
            print('Found Android device with device name:', DEVICE_NAME)
            print('Device MAC address:', device.addr)  # Display the MAC address of the device
            scan_data = device.getScanData()  # Get scan data
            for (adtype, desc, value) in scan_data:
                if desc == '16b Service Data':  # If the description is '16b Service Data'
                    hello_hex = value[4:]  # Extract the remaining part of the data after skipping the first 4 characters (metadata)
                    hello_str = binascii.unhexlify(hello_hex).decode('utf-8')  # Convert binary data to ASCII string
                    print('Received hello message:', hello_str)  # Display the received hello message
            try:
                time.sleep(3.0)  # Insert a delay to allow the device to prepare for connection
                peripheral = btle.Peripheral(device)  # Try to connect to the device
                break
            except btle.BTLEException as ex:
                print('Failed to connect to the device:', str(ex))
                continue

if peripheral is None:
    print('Could not find or connect to the device with MAC address:', MAC_ADDRESS)
else:
    peripheral.setDelegate(MyDelegate())  # Set the delegate

    try:
        service = peripheral.getServiceByUUID(MY_SERVICE_UUID)  # Get the service
        characteristic = service.getCharacteristics(MY_CHARACTERISTIC_UUID)[0]  # Get the characteristic
    except btle.BTLEException as ex:
        print('Failed to get service or characteristic:', str(ex))

    while True:  # Loop to continuously receive notifications
        try:
            if peripheral.waitForNotifications(3.0):  # Wait for notifications (1 second)
                continue
        except btle.BTLEException as ex:
            print('Connection lost:', str(ex))
            break
