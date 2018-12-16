# Linux Input Binding

This project provides an Eclipse Smarthome and Openhab2 compatible binding to
input devices under Linux.
It also provides a fairly generic Java binding to the libevdev library.
Currently only keyboards are handled.
The binding supports all kinds keyboards, irrespective on how they are
attached. (USB, Bluetooth, serial...)

## Current status

Beta.

This project is in an early development phase.
The basics should be usable but the ergonomics are not completely there yet.

## Installation instructions

The user running will have to have full access to the input device.
(Instructions will be provided how to wire everything up with Udev)
Libevdev has to be installed. It should be available through your
operating systems packagemanager.

## Usage

### Configuration

The Thing has to be configured with the path of the input device to access.
This should be in the form of `/dev/input/eventX` where `X` is specific to your device.
You also have to active the Thing in the configuration.
When activated *only* the Thing will receive events from the input device.

### Channels

Each Thing provides multiple channels

* A `key` channel that aggregates all events.
* Per physical key channels.

### Events

The following happens when pressing and releasing a key:

#### Press

1) State of global key channel updated to new key.
2) State of per-key channel updated to `"CLOSED"`.
3) Global key channel triggered with the current key name.
4) Per-key channel triggered with `"PRESSED"`".
5) State of global key channel updated to `""` (Empty string)

#### Release

1) State of per-key channel updated to `"OPEN"`
2) Per-key channel triggered with `"RELEASED"`

#### Rationale

Channel states are updated first to allow rules triggered by channel triggers to access the new state.

## Implementation note

Contrary to other Smarthome/Openhab plugins, this plugin uses the maven-bundle-plugin for the project setup.
This may affect your development experience.
It also removes a lot of boilerplate from the project.

## Tested with

* Openhab 2.3.0

## Keywords

* Openhab
* Eclipse Smarthome
* Evdev
* Linux
* Input
* Keyboard
* USB
* Bluetooth
