# Unity Native Camera Plugin

**Available on Asset Store:** https://assetstore.unity.com/packages/tools/integration/native-camera-for-android-ios-117802

**Forum Thread:** https://forum.unity.com/threads/native-camera-for-android-ios-open-source.529560/

**Discord:** https://discord.gg/UJJt549AaV

**[Support the Developer ☕](https://yasirkula.itch.io/unity3d)**

This plugin helps you take pictures/record videos natively with your device's camera on Android and iOS. It has built-in support for runtime permissions, as well.

## INSTALLATION

There are 5 ways to install this plugin:

- import [NativeCamera.unitypackage](https://github.com/yasirkula/UnityNativeCamera/releases) via *Assets-Import Package*
- clone/[download](https://github.com/yasirkula/UnityNativeCamera/archive/master.zip) this repository and move the *Plugins* folder to your Unity project's *Assets* folder
- import it from [Asset Store](https://assetstore.unity.com/packages/tools/integration/native-camera-for-android-ios-117802)
- *(via Package Manager)* add the following line to *Packages/manifest.json*:
  - `"com.yasirkula.nativecamera": "https://github.com/yasirkula/UnityNativeCamera.git",`
- *(via [OpenUPM](https://openupm.com))* after installing [openupm-cli](https://github.com/openupm/openupm-cli), run the following command:
  - `openupm add com.yasirkula.nativecamera`

### Android Setup

NativeCamera no longer requires any manual setup on Android. If you were using an older version of the plugin, you need to remove NativeCamera's `<provider ... />` from your *AndroidManifest.xml*.

For reference, the legacy documentation is available at: https://github.com/yasirkula/UnityNativeCamera/wiki/Manual-Setup-for-Android

### iOS Setup

There are two ways to set up the plugin on iOS:

- **a. Automated Setup:** *(optional)* change the values of **Camera Usage Description** and **Microphone Usage Description** at *Project Settings/yasirkula/Native Camera*
- **b. Manual Setup:** see: https://github.com/yasirkula/UnityNativeCamera/wiki/Manual-Setup-for-iOS

## FAQ

- **Can't use the camera, it says "java.lang.ClassNotFoundException: com.yasirkula.unity.NativeCamera" in Logcat**

If you are sure that your plugin is up-to-date, then enable **Custom Proguard File** option from *Player Settings* and add the following line to that file: `-keep class com.yasirkula.unity.* { *; }`

- **NativeCamera functions return Permission.Denied even though I've granted the permission"**

Declare `WRITE_EXTERNAL_STORAGE` permission manually in your [**Plugins/Android/AndroidManifest.xml** file](https://answers.unity.com/questions/982710/where-is-the-manifest-file-in-unity.html) with the `tools:node="replace"` attribute as follows: `<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" tools:node="replace"/>` (you'll need to add the `xmlns:tools="http://schemas.android.com/tools"` attribute to the `<manifest ...>` element).

## HOW TO

### A. Accessing Camera

`NativeCamera.TakePicture( CameraCallback callback, int maxSize = -1, PreferredCamera preferredCamera = PreferredCamera.Default )`: opens the camera and waits for user to take a picture.
- This operation is **asynchronous**! After user takes a picture or cancels the operation, the **callback** is called (on main thread). **CameraCallback** takes a *string* parameter which stores the path of the captured image, or *null* if the operation is canceled
- **maxSize** determines the maximum size of the returned image in pixels on iOS. A larger image will be down-scaled for better performance. If untouched, its value will be set to *SystemInfo.maxTextureSize*. Has no effect on Android
- **saveAsJPEG** determines whether the image is saved as JPEG or PNG. Has no effect on Android
- **preferredCamera** determines whether the rear camera or the front camera should be opened by default

`NativeCamera.RecordVideo( CameraCallback callback, Quality quality = Quality.Default, int maxDuration = 0, long maxSizeBytes = 0L, PreferredCamera preferredCamera = PreferredCamera.Default )`: opens the camera and waits for user to record a video.
- **quality** determines the quality of the recorded video. Available values are: *Default*, *Low*, *Medium*, *High*
- **maxDuration** determines the maximum duration, in seconds, for the recorded video. If untouched, there will be no limit. Please note that the functionality of this parameter depends on whether the device vendor has added this capability to the camera or not. So, this parameter may not have any effect on some devices
- **maxSizeBytes** determines the maximum size, in bytes, for the recorded video. If untouched, there will be no limit. This parameter has no effect on iOS. Please note that the functionality of this parameter depends on whether the device vendor has added this capability to the camera or not. So, this parameter may not have any effect on some devices

`NativeCamera.DeviceHasCamera()`: returns false if the device doesn't have a camera. In this case, TakePicture and RecordVideo functions will not execute.

`NativeCamera.IsCameraBusy()`: returns true if the camera is currently open. In that case, another TakePicture or RecordVideo request will simply be ignored.

Note that TakePicture and RecordVideo functions return a *NativeCamera.Permission* value. More details available below.

### B. Runtime Permissions

Beginning with *6.0 Marshmallow*, Android apps must request runtime permissions before accessing certain services, similar to iOS. There are two functions to handle permissions with this plugin:

`NativeCamera.Permission NativeCamera.CheckPermission()`: checks whether the app has access to camera or not.

**NativeCamera.Permission** is an enum that can take 3 values: 
- **Granted**: we have the permission to access the camera
- **ShouldAsk**: we don't have permission yet, but we can ask the user for permission via *RequestPermission* function (see below). On Android, as long as the user doesn't select "Don't ask again" while denying the permission, ShouldAsk is returned
- **Denied**: we don't have permission and we can't ask the user for permission. In this case, user has to give the permission from Settings. This happens when user denies the permission on iOS (can't request permission again on iOS), when user selects "Don't ask again" while denying the permission on Android or when user is not allowed to give that permission (parental controls etc.)

`NativeCamera.Permission NativeCamera.RequestPermission()`: requests permission to access the camera from the user and returns the result. It is recommended to show a brief explanation before asking the permission so that user understands why the permission is needed and doesn't click Deny or worse, "Don't ask again". Note that TakePicture and RecordVideo functions call RequestPermission internally and execute only if the permission is granted (the result of RequestPermission is then returned).

`NativeCamera.OpenSettings()`: opens the settings for this app, from where the user can manually grant permission in case current permission state is *Permission.Denied* (Android requires *Storage* and, if declared in AndroidManifest, *Camera* permissions; iOS requires *Camera* permission).

`bool NativeCamera.CanOpenSettings()`: on iOS versions prior to 8.0, opening settings from within the app is not possible and in this case, this function returns *false*. Otherwise, it returns *true*.

### C. Utility Functions

`NativeCamera.ImageProperties NativeCamera.GetImageProperties( string imagePath )`: returns an *ImageProperties* instance that holds the width, height, mime type and EXIF orientation information of an image file without creating a *Texture2D* object. Mime type will be *null*, if it can't be determined.

`NativeCamera.VideoProperties NativeCamera.GetVideoProperties( string videoPath )`: returns a *VideoProperties* instance that holds the width, height, duration (in milliseconds) and rotation information of a video file. To play a video in correct orientation, you should rotate it by *rotation* degrees clockwise. For a 90-degree or 270-degree rotated video, values of *width* and *height* should be swapped to get the display size of the video.

`Texture2D NativeCamera.LoadImageAtPath( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )`: creates a Texture2D from the specified image file in correct orientation and returns it. Returns *null*, if something goes wrong.
- **maxSize** determines the maximum size of the returned Texture2D in pixels. Larger textures will be down-scaled. If untouched, its value will be set to *SystemInfo.maxTextureSize*. It is recommended to set a proper maxSize for better performance
- **markTextureNonReadable** marks the generated texture as non-readable for better memory usage. If you plan to modify the texture later (e.g. *GetPixels*/*SetPixels*), set its value to *false*
- **generateMipmaps** determines whether texture should have mipmaps or not
- **linearColorSpace** determines whether texture should be in linear color space or sRGB color space

`Texture2D NativeCamera.GetVideoThumbnail( string videoPath, int maxSize = -1, double captureTimeInSeconds = -1.0, bool markTextureNonReadable = true )`: creates a Texture2D thumbnail from a video file and returns it. Returns *null*, if something goes wrong.
- **maxSize** determines the maximum size of the returned Texture2D in pixels. Larger thumbnails will be down-scaled. If untouched, its value will be set to *SystemInfo.maxTextureSize*. It is recommended to set a proper maxSize for better performance
- **captureTimeInSeconds** determines the frame of the video that the thumbnail is captured from. If untouched, OS will decide this value
- **markTextureNonReadable** (see *LoadImageAtPath*)

## EXAMPLE CODE

The following code has two functions:

- if you click left half of the screen, the camera is opened and after a picture is taken, it is displayed on a temporary quad that is placed in front of the camera
- if you click right half of the screen, the camera is opened and after a video is recorded, it is played using the *Handheld.PlayFullScreenMovie* function

```csharp
void Update()
{
	if( Input.GetMouseButtonDown( 0 ) )
	{
		// Don't attempt to use the camera if it is already open
		if( NativeCamera.IsCameraBusy() )
			return;
			
		if( Input.mousePosition.x < Screen.width / 2 )
		{
			// Take a picture with the camera
			// If the captured image's width and/or height is greater than 512px, down-scale it
			TakePicture( 512 );
		}
		else
		{
			// Record a video with the camera
			RecordVideo();
		}
	}
}

private void TakePicture( int maxSize )
{
	NativeCamera.Permission permission = NativeCamera.TakePicture( ( path ) =>
	{
		Debug.Log( "Image path: " + path );
		if( path != null )
		{
			// Create a Texture2D from the captured image
			Texture2D texture = NativeCamera.LoadImageAtPath( path, maxSize );
			if( texture == null )
			{
				Debug.Log( "Couldn't load texture from " + path );
				return;
			}

			// Assign texture to a temporary quad and destroy it after 5 seconds
			GameObject quad = GameObject.CreatePrimitive( PrimitiveType.Quad );
			quad.transform.position = Camera.main.transform.position + Camera.main.transform.forward * 2.5f;
			quad.transform.forward = Camera.main.transform.forward;
			quad.transform.localScale = new Vector3( 1f, texture.height / (float) texture.width, 1f );
			
			Material material = quad.GetComponent<Renderer>().material;
			if( !material.shader.isSupported ) // happens when Standard shader is not included in the build
				material.shader = Shader.Find( "Legacy Shaders/Diffuse" );

			material.mainTexture = texture;
				
			Destroy( quad, 5f );

			// If a procedural texture is not destroyed manually, 
			// it will only be freed after a scene change
			Destroy( texture, 5f );
		}
	}, maxSize );

	Debug.Log( "Permission result: " + permission );
}

private void RecordVideo()
{
	NativeCamera.Permission permission = NativeCamera.RecordVideo( ( path ) =>
	{
		Debug.Log( "Video path: " + path );
		if( path != null )
		{
			// Play the recorded video
			Handheld.PlayFullScreenMovie( "file://" + path );
		}
	} );

	Debug.Log( "Permission result: " + permission );
}
```
