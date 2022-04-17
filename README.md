# BlinkScroll

An Android Accessibility Service that monitors a user's face via the phone's front-facing camera.
When the user blinks (or winks), the service performs a global swipe gesture.
It's great for hands-free swiping through videos on TikTok/Instagram ;).

![Gif of user blinking to swipe through TikTok](.docs/blink.gif "Blinking to Swipe")

**This is a very quick-and-dirty demo, but may be of use if you are:
A) Looking to use the CameraX APIs for real-time image processing.
B) Looking to create an `AccessibilityService` that performs actions on behalf of the user.
C) Looking for a "full", working example of the MLKit Face Recognition APIs.