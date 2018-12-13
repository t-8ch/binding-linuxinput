# Linux Input Binding

This project provides an Eclipse Smarhome and Openhab 2 compatible binding to
inputdevices under Linux.
It also provides a fairly generic Java binding to the libevdev library.
Currently only keyboards are handled.
The binding supports all kinds keyboards, irrespective on how they are
attached. (USB, Bluetooth, serial...)

## Current status

Alpha.

This project is in an early development phase.
The basics should be usable but the ergonomics are not completely there yet.

## Installation instructions

The user running will have to have full access to the input device.
(Instructions will be provided how to wire everything up with Udev)
Libevdev has to be installed. It should be available through your
operating systems packagemanager.

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
