= Native Camera for Android & iOS (v1.3.8) =

Online documentation & example code available at: https://github.com/yasirkula/UnityNativeCamera
E-mail: yasirkula@gmail.com


1. ABOUT
This plugin helps you take pictures/record videos natively with your device's camera on Android & iOS.


2. HOW TO
NativeCamera no longer requires any manual setup on Android. If you were using an older version of the plugin, you need to remove NativeCamera's "<provider ... />" from your AndroidManifest.xml.

For reference, the legacy documentation is available at: https://github.com/yasirkula/UnityNativeCamera/wiki/Manual-Setup-for-Android

2.2. iOS Setup
There are two ways to set up the plugin on iOS:

2.2.a. Automated Setup for iOS
- (optional) change the values of 'Camera Usage Description' and 'Microphone Usage Description' at 'Project Settings/yasirkula/Native Camera'

2.2.b. Manual Setup for iOS
- set the value of 'Automated Setup' to false at 'Project Settings/yasirkula/Native Camera'
- build your project
- enter a Camera Usage Description to Info.plist in Xcode
- insert "-framework MobileCoreServices -framework ImageIO" to the "Other Linker Flags" of Unity-iPhone Target (and UnityFramework Target on Unity 2019.3 or newer)


3. FAQ
- Can't use the camera, it says "java.lang.ClassNotFoundException: com.yasirkula.unity.NativeCamera" in Logcat
If you are sure that your plugin is up-to-date, then enable "Custom Proguard File" option from Player Settings and add the following line to that file: -keep class com.yasirkula.unity.* { *; }


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
// saveAsJPEG: determines whether the image is saved as JPEG or PNG. Has no effect on Android
// preferredCamera: determines whether the rear camera or the front camera should be opened by default
NativeCamera.Permission NativeCamera.TakePicture( CameraCallback callback, int maxSize = -1, bool saveAsJPEG = true, PreferredCamera preferredCamera = PreferredCamera.Default );

// quality: determines the quality of the recorded video
// maxDuration: determines the maximum duration, in seconds, for the recorded video. If untouched, there will be no limit. Please note that the functionality of this parameter depends on whether the device vendor has added this capability to the camera or not. So, this parameter may not have any effect on some devices
// maxSizeBytes: determines the maximum size, in bytes, for the recorded video. If untouched, there will be no limit. This parameter has no effect on iOS. Please note that the functionality of this parameter depends on whether the device vendor has added this capability to the camera or not. So, this parameter may not have any effect on some devices
NativeCamera.Permission NativeCamera.RecordVideo( CameraCallback callback, Quality quality = Quality.Default, int maxDuration = 0, long maxSizeBytes = 0L, PreferredCamera preferredCamera = PreferredCamera.Default );

bool NativeCamera.DeviceHasCamera(); // returns false if the device doesn't have a camera. In this case, TakePicture and RecordVideo functions will not execute

bool NativeCamera.IsCameraBusy(); // returns true if the camera is currently open. In that case, another TakePicture or RecordVideo request will simply be ignored


//// Runtime Permissions ////

// Accessing camera is only possible when permission state is Permission.Granted. TakePicture and RecordVideo functions request permission internally (and return the result) but you can also check/request the permissions manually
// isPicturePermission: whether we're checking permission to take a picture or record a video. Has no effect on iOS
NativeCamera.Permission NativeCamera.CheckPermission( bool isPicturePermission );
NativeCamera.Permission NativeCamera.RequestPermission( bool isPicturePermission );

// If permission state is Permission.Denied, user must grant the necessary permission(s) manually from the Settings (Android requires Storage and, if declared in AndroidManifest, Camera permissions; iOS requires Camera permission). These functions help you open the Settings directly from within the app
void NativeCamera.OpenSettings();
bool NativeCamera.CanOpenSettings();


//// Utility Functions ////

// Creates a Texture2D from the specified image file in correct orientation and returns it. Returns null, if something goes wrong
// maxSize: determines the maximum size of the returned Texture2D in pixels. Larger textures will be down-scaled. If untouched, its value will be set to SystemInfo.maxTextureSize. It is recommended to set a proper maxSize for better performance
// markTextureNonReadable: marks the generated texture as non-readable for better memory usage. If you plan to modify the texture later (e.g. GetPixels/SetPixels), set its value to false
// generateMipmaps: determines whether texture should have mipmaps or not
// linearColorSpace: determines whether texture should be in linear color space or sRGB color space
Texture2D NativeCamera.LoadImageAtPath( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false );
async Task<Texture2D> NativeCamera.LoadImageAtPathAsync( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false );

// Creates a Texture2D thumbnail from a video file and returns it. Returns null, if something goes wrong
// maxSize: determines the maximum size of the returned Texture2D in pixels. Larger thumbnails will be down-scaled. If untouched, its value will be set to SystemInfo.maxTextureSize. It is recommended to set a proper maxSize for better performance
// captureTimeInSeconds: determines the frame of the video that the thumbnail is captured from. If untouched, OS will decide this value
// markTextureNonReadable: see LoadImageAtPath
// generateMipmaps: see LoadImageAtPath
// linearColorSpace: see LoadImageAtPath
Texture2D NativeCamera.GetVideoThumbnail( string videoPath, int maxSize = -1, double captureTimeInSeconds = -1.0, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false );
async Task<Texture2D> NativeCamera.GetVideoThumbnailAsync( string videoPath, int maxSize = -1, double captureTimeInSeconds = -1.0, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false );

// Returns an ImageProperties instance that holds the width, height and mime type information of an image file without creating a Texture2D object. Mime type will be null, if it can't be determined
NativeCamera.ImageProperties NativeCamera.GetImageProperties( string imagePath );

// Returns a VideoProperties instance that holds the width, height, duration (in milliseconds) and rotation information of a video file. To play a video in correct orientation, you should rotate it by rotation degrees clockwise. For a 90-degree or 270-degree rotated video, values of width and height should be swapped to get the display size of the video
NativeCamera.VideoProperties NativeCamera.GetVideoProperties( string videoPath );