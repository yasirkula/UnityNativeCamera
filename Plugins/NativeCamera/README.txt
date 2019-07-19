= Native Camera for Android & iOS =

Online documentation & example code available at: https://github.com/yasirkula/UnityNativeCamera
E-mail: yasirkula@gmail.com

1. ABOUT
This plugin helps you take pictures/record videos natively with your device's camera on Android & iOS.

2. HOW TO
2.1. Android Setup
- set "Write Permission" to "External (SDCard)" in Player Settings
- NativeCamera requires a small modification in AndroidManifest. If your project does not have an AndroidManifest.xml file located at Assets/Plugins/Android, you should copy Unity's default AndroidManifest.xml from C:\Program Files\Unity\Editor\Data\PlaybackEngines\AndroidPlayer (it might be located in a subfolder, like 'Apk') to Assets/Plugins/Android. Inside the <application>...</application> tag of your AndroidManifest, insert the following code snippet:

<provider
  android:name="com.yasirkula.unity.NativeCameraContentProvider"
  android:authorities="MY_UNIQUE_AUTHORITY"
  android:exported="false"
  android:grantUriPermissions="true" />

Here, you should change MY_UNIQUE_AUTHORITY with a unique string. That is important because two apps with the same android:authorities string in their <provider> tag can't be installed on the same device. Just make it something unique, like your bundle identifier, if you like.

To verify this step, you can check the contents of Temp/StagingArea/AndroidManifest.xml to see if the <provider ... /> is still there after building your project to Android.

NOTE: if you are also using the NativeShare plugin, make sure that each plugin's provider has a different android:authorities string.

- (optional) inside the <manifest>...</manifest> tag of your AndroidManifest, insert <uses-feature android:name="android.hardware.camera" android:required="false" /> to declare that your app benefits from camera (if your app requires a camera/can't run without one, then change the value of android:required to true)

2.2. iOS Setup
There are two ways to set up the plugin on iOS:

2.2.a. Automated Setup for iOS
- (optional) change the value of CAMERA_USAGE_DESCRIPTION in Plugins/NativeCamera/Editor/NCPostProcessBuild.cs

2.2.b. Manual Setup for iOS
- set the value of ENABLED to false in NCPostProcessBuild.cs
- build your project
- enter a Camera Usage Description to Info.plist in Xcode
- insert "-framework MobileCoreServices -framework ImageIO" to the "Other Linker Flags" of Unity-iPhone Target

3. FAQ
- Can't use the camera, it says "Can't find ContentProvider, camera is inaccessible!" in Logcat
Make sure that you've added the provider to the AndroidManifest.xml located exactly at Assets/Plugins/Android and verify that it is inserted in-between the <application>...</application> tags.

- Can't use the camera, it says "java.lang.ClassNotFoundException: com.yasirkula.unity.NativeCamera" in Logcat
If your project uses ProGuard, try adding the following line to ProGuard filters: -keep class com.yasirkula.unity.* { *; }

- My app crashes at startup after importing NativeCamera to my project
Make sure that you didn't touch the provider's android:name value, it must stay as is. You only need to change the android:authorities string.

4. SCRIPTING API
Please see the online documentation for a more in-depth documentation of the Scripting API: https://github.com/yasirkula/UnityNativeCamera

enum NativeCamera.Permission { Denied = 0, Granted = 1, ShouldAsk = 2 };
enum NativeCamera.Quality { Default = -1, Low = 0, Medium = 1, High = 2 };
enum NativeCamera.ImageOrientation { Unknown = -1, Normal = 0, Rotate90 = 1, Rotate180 = 2, Rotate270 = 3, FlipHorizontal = 4, Transpose = 5, FlipVertical = 6, Transverse = 7 }; // EXIF orientation: http://sylvana.net/jpegcrop/exif_orientation.html (indices are reordered)

delegate void CameraCallback( string path );

//// Accessing Camera ////

// This operation is asynchronous! After user takes a picture or cancels the operation, the callback is called (on main thread)
// CameraCallback takes a string parameter which stores the path of the captured image, or null if the operation is canceled
// maxSize: determines the maximum size of the returned image in pixels on iOS. A larger image will be down-scaled for better performance. If untouched, its value will be set to SystemInfo.maxTextureSize. Has no effect on Android
NativeCamera.Permission NativeCamera.TakePicture( CameraCallback callback, int maxSize = -1 );

// quality: determines the quality of the recorded video
// maxDuration: determines the maximum duration, in seconds, for the recorded video. If untouched, there will be no limit. Please note that the functionality of this parameter depends on whether the device vendor has added this capability to the camera or not. So, this parameter may not have any effect on some devices
// maxSizeBytes: determines the maximum size, in bytes, for the recorded video. If untouched, there will be no limit. This parameter has no effect on iOS. Please note that the functionality of this parameter depends on whether the device vendor has added this capability to the camera or not. So, this parameter may not have any effect on some devices
NativeCamera.Permission NativeCamera.RecordVideo( CameraCallback callback, Quality quality = Quality.Default, int maxDuration = 0, long maxSizeBytes = 0L );

bool NativeCamera.DeviceHasCamera(); // returns false if the device doesn't have a camera. In this case, TakePicture and RecordVideo functions will not execute

bool NativeCamera.IsCameraBusy(); // returns true if the camera is currently open. In that case, another TakePicture or RecordVideo request will simply be ignored


//// Runtime Permissions ////

// Accessing camera is only possible when permission state is Permission.Granted. TakePicture and RecordVideo functions request permission internally (and return the result) but you can also check/request the permissions manually
NativeCamera.Permission NativeCamera.CheckPermission();
NativeCamera.Permission NativeCamera.RequestPermission();

// If permission state is Permission.Denied, user must grant the necessary permission(s) manually from the Settings (Android requires Storage and, if declared in AndroidManifest, Camera permissions; iOS requires Camera permission). These functions help you open the Settings directly from within the app
void NativeCamera.OpenSettings();
bool NativeCamera.CanOpenSettings();


//// Utility Functions ////

// maxSize: determines the maximum size of the returned Texture2D in pixels. Larger textures will be down-scaled. If untouched, its value will be set to SystemInfo.maxTextureSize. It is recommended to set a proper maxSize for better performance
// markTextureNonReadable: marks the generated texture as non-readable for better memory usage. If you plan to modify the texture later (e.g. GetPixels/SetPixels), set its value to false
// generateMipmaps: determines whether texture should have mipmaps or not
// linearColorSpace: determines whether texture should be in linear color space or sRGB color space
Texture2D NativeCamera.LoadImageAtPath( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false ): creates a Texture2D from the specified image file in correct orientation and returns it. Returns null, if something goes wrong

NativeCamera.ImageProperties NativeCamera.GetImageProperties( string imagePath ): returns an ImageProperties instance that holds the width, height and mime type information of an image file without creating a Texture2D object. Mime type will be null, if it can't be determined