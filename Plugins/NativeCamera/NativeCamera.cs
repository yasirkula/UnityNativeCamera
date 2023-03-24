using System;
using System.Globalization;
using System.IO;
using UnityEngine;
#if UNITY_2018_4_OR_NEWER && !NATIVE_CAMERA_DISABLE_ASYNC_FUNCTIONS
using System.Threading.Tasks;
using Unity.Collections;
using UnityEngine.Networking;
#endif
#if UNITY_ANDROID || UNITY_IOS
using NativeCameraNamespace;
#endif
using Object = UnityEngine.Object;

public static class NativeCamera
{
	public struct ImageProperties
	{
		public readonly int width;
		public readonly int height;
		public readonly string mimeType;
		public readonly ImageOrientation orientation;

		public ImageProperties( int width, int height, string mimeType, ImageOrientation orientation )
		{
			this.width = width;
			this.height = height;
			this.mimeType = mimeType;
			this.orientation = orientation;
		}
	}

	public struct VideoProperties
	{
		public readonly int width;
		public readonly int height;
		public readonly long duration;
		public readonly float rotation;

		public VideoProperties( int width, int height, long duration, float rotation )
		{
			this.width = width;
			this.height = height;
			this.duration = duration;
			this.rotation = rotation;
		}
	}

	public enum Permission { Denied = 0, Granted = 1, ShouldAsk = 2 };
	public enum Quality { Default = -1, Low = 0, Medium = 1, High = 2 };
	public enum PreferredCamera { Default = -1, Rear = 0, Front = 1 }

	// EXIF orientation: http://sylvana.net/jpegcrop/exif_orientation.html (indices are reordered)
	public enum ImageOrientation { Unknown = -1, Normal = 0, Rotate90 = 1, Rotate180 = 2, Rotate270 = 3, FlipHorizontal = 4, Transpose = 5, FlipVertical = 6, Transverse = 7 };

	public delegate void CameraCallback( string path );

	#region Platform Specific Elements
#if !UNITY_EDITOR && UNITY_ANDROID
	private static AndroidJavaClass m_ajc = null;
	private static AndroidJavaClass AJC
	{
		get
		{
			if( m_ajc == null )
				m_ajc = new AndroidJavaClass( "com.yasirkula.unity.NativeCamera" );

			return m_ajc;
		}
	}

	private static AndroidJavaObject m_context = null;
	private static AndroidJavaObject Context
	{
		get
		{
			if( m_context == null )
			{
				using( AndroidJavaObject unityClass = new AndroidJavaClass( "com.unity3d.player.UnityPlayer" ) )
				{
					m_context = unityClass.GetStatic<AndroidJavaObject>( "currentActivity" );
				}
			}

			return m_context;
		}
	}
#elif !UNITY_EDITOR && UNITY_IOS
	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern int _NativeCamera_CheckPermission();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern int _NativeCamera_RequestPermission();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern int _NativeCamera_CanOpenSettings();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _NativeCamera_OpenSettings();

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern int _NativeCamera_HasCamera();
	
	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _NativeCamera_TakePicture( string imageSavePath, int maxSize, int preferredCamera );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern void _NativeCamera_RecordVideo( int quality, int maxDuration, int preferredCamera );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern string _NativeCamera_GetImageProperties( string path );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern string _NativeCamera_GetVideoProperties( string path );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern string _NativeCamera_GetVideoThumbnail( string path, string thumbnailSavePath, int maxSize, double captureTimeInSeconds );

	[System.Runtime.InteropServices.DllImport( "__Internal" )]
	private static extern string _NativeCamera_LoadImageAtPath( string path, string temporaryFilePath, int maxSize );
#endif

#if !UNITY_EDITOR && ( UNITY_ANDROID || UNITY_IOS )
	private static string m_temporaryImagePath = null;
	private static string TemporaryImagePath
	{
		get
		{
			if( m_temporaryImagePath == null )
			{
				m_temporaryImagePath = Path.Combine( Application.temporaryCachePath, "tmpImg" );
				Directory.CreateDirectory( Application.temporaryCachePath );
			}

			return m_temporaryImagePath;
		}
	}
#endif

#if !UNITY_EDITOR && UNITY_IOS
	private static string m_iOSSelectedImagePath = null;
	private static string IOSSelectedImagePath
	{
		get
		{
			if( m_iOSSelectedImagePath == null )
			{
				m_iOSSelectedImagePath = Path.Combine( Application.temporaryCachePath, "CameraImg" );
				Directory.CreateDirectory( Application.temporaryCachePath );
			}

			return m_iOSSelectedImagePath;
		}
	}
#endif
	#endregion

	#region Runtime Permissions
	public static Permission CheckPermission( bool isPicturePermission )
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		Permission result = (Permission) AJC.CallStatic<int>( "CheckPermission", Context, isPicturePermission );
		if( result == Permission.Denied && (Permission) PlayerPrefs.GetInt( "NativeCameraPermission", (int) Permission.ShouldAsk ) == Permission.ShouldAsk )
			result = Permission.ShouldAsk;

		return result;
#elif !UNITY_EDITOR && UNITY_IOS
		return (Permission) _NativeCamera_CheckPermission();
#else
		return Permission.Granted;
#endif
	}

	public static Permission RequestPermission( bool isPicturePermission )
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		object threadLock = new object();
		lock( threadLock )
		{
			NCPermissionCallbackAndroid nativeCallback = new NCPermissionCallbackAndroid( threadLock );

			AJC.CallStatic( "RequestPermission", Context, nativeCallback, isPicturePermission, PlayerPrefs.GetInt( "NativeCameraPermission", (int) Permission.ShouldAsk ) );

			if( nativeCallback.Result == -1 )
				System.Threading.Monitor.Wait( threadLock );

			if( (Permission) nativeCallback.Result != Permission.ShouldAsk && PlayerPrefs.GetInt( "NativeCameraPermission", -1 ) != nativeCallback.Result )
			{
				PlayerPrefs.SetInt( "NativeCameraPermission", nativeCallback.Result );
				PlayerPrefs.Save();
			}

			return (Permission) nativeCallback.Result;
		}
#elif !UNITY_EDITOR && UNITY_IOS
		return (Permission) _NativeCamera_RequestPermission();
#else
		return Permission.Granted;
#endif
	}

	public static bool CanOpenSettings()
	{
#if !UNITY_EDITOR && UNITY_IOS
		return _NativeCamera_CanOpenSettings() == 1;
#else
		return true;
#endif
	}

	public static void OpenSettings()
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		AJC.CallStatic( "OpenSettings", Context );
#elif !UNITY_EDITOR && UNITY_IOS
		_NativeCamera_OpenSettings();
#endif
	}
	#endregion

	#region Camera Functions
	public static Permission TakePicture( CameraCallback callback, int maxSize = -1, bool saveAsJPEG = true, PreferredCamera preferredCamera = PreferredCamera.Default )
	{
		Permission result = RequestPermission( true );
		if( result == Permission.Granted && !IsCameraBusy() )
		{
#if UNITY_EDITOR
			string pickedFile = UnityEditor.EditorUtility.OpenFilePanelWithFilters( "Select image", "", new string[] { "Image files", "png,jpg,jpeg", "All files", "*" } );

			if( callback != null )
				callback( pickedFile != "" ? pickedFile : null );
#elif UNITY_ANDROID
			AJC.CallStatic( "TakePicture", Context, new NCCameraCallbackAndroid( callback ), (int) preferredCamera );
#elif UNITY_IOS
			if( maxSize <= 0 )
				maxSize = SystemInfo.maxTextureSize;

			NCCameraCallbackiOS.Initialize( callback );
			_NativeCamera_TakePicture( IOSSelectedImagePath + ( saveAsJPEG ? ".jpeg" : ".png" ), maxSize, (int) preferredCamera );
#else
			if( callback != null )
				callback( null );
#endif
		}

		return result;
	}

	public static Permission RecordVideo( CameraCallback callback, Quality quality = Quality.Default, int maxDuration = 0, long maxSizeBytes = 0L, PreferredCamera preferredCamera = PreferredCamera.Default )
	{
		Permission result = RequestPermission( false );
		if( result == Permission.Granted && !IsCameraBusy() )
		{
#if UNITY_EDITOR
			string pickedFile = UnityEditor.EditorUtility.OpenFilePanelWithFilters( "Select video", "", new string[] { "Video files", "mp4,mov,wav,avi", "All files", "*" } );

			if( callback != null )
				callback( pickedFile != "" ? pickedFile : null );
#elif UNITY_ANDROID
			AJC.CallStatic( "RecordVideo", Context, new NCCameraCallbackAndroid( callback ), (int) preferredCamera, (int) quality, maxDuration, maxSizeBytes );
#elif UNITY_IOS
			NCCameraCallbackiOS.Initialize( callback );
			_NativeCamera_RecordVideo( (int) quality, maxDuration, (int) preferredCamera );
#else
			if( callback != null )
				callback( null );
#endif
		}

		return result;
	}

	public static bool DeviceHasCamera()
	{
#if !UNITY_EDITOR && UNITY_ANDROID
		return AJC.CallStatic<bool>( "HasCamera", Context );
#elif !UNITY_EDITOR && UNITY_IOS
		return _NativeCamera_HasCamera() == 1;
#else
		return true;
#endif
	}

	public static bool IsCameraBusy()
	{
#if !UNITY_EDITOR && UNITY_IOS
		return NCCameraCallbackiOS.IsBusy;
#else
		return false;
#endif
	}
	#endregion

	#region Utility Functions
	public static Texture2D LoadImageAtPath( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )
	{
		if( string.IsNullOrEmpty( imagePath ) )
			throw new ArgumentException( "Parameter 'imagePath' is null or empty!" );

		if( !File.Exists( imagePath ) )
			throw new FileNotFoundException( "File not found at " + imagePath );

		if( maxSize <= 0 )
			maxSize = SystemInfo.maxTextureSize;

#if !UNITY_EDITOR && UNITY_ANDROID
		string loadPath = AJC.CallStatic<string>( "LoadImageAtPath", Context, imagePath, TemporaryImagePath, maxSize );
#elif !UNITY_EDITOR && UNITY_IOS
		string loadPath = _NativeCamera_LoadImageAtPath( imagePath, TemporaryImagePath, maxSize );
#else
		string loadPath = imagePath;
#endif

		string extension = Path.GetExtension( imagePath ).ToLowerInvariant();
		TextureFormat format = ( extension == ".jpg" || extension == ".jpeg" ) ? TextureFormat.RGB24 : TextureFormat.RGBA32;

		Texture2D result = new Texture2D( 2, 2, format, generateMipmaps, linearColorSpace );

		try
		{
			if( !result.LoadImage( File.ReadAllBytes( loadPath ), markTextureNonReadable ) )
			{
				Debug.LogWarning( "Couldn't load image at path: " + loadPath );

				Object.DestroyImmediate( result );
				return null;
			}
		}
		catch( Exception e )
		{
			Debug.LogException( e );

			Object.DestroyImmediate( result );
			return null;
		}
		finally
		{
			if( loadPath != imagePath )
			{
				try
				{
					File.Delete( loadPath );
				}
				catch { }
			}
		}

		return result;
	}

#if UNITY_2018_4_OR_NEWER && !NATIVE_CAMERA_DISABLE_ASYNC_FUNCTIONS
	public static async Task<Texture2D> LoadImageAtPathAsync( string imagePath, int maxSize = -1, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )
	{
		if( string.IsNullOrEmpty( imagePath ) )
			throw new ArgumentException( "Parameter 'imagePath' is null or empty!" );

		if( !File.Exists( imagePath ) )
			throw new FileNotFoundException( "File not found at " + imagePath );

		if( maxSize <= 0 )
			maxSize = SystemInfo.maxTextureSize;

#if !UNITY_EDITOR && UNITY_ANDROID
		string temporaryImagePath = TemporaryImagePath; // Must be accessed from main thread
		string loadPath = await TryCallNativeAndroidFunctionOnSeparateThread( () => AJC.CallStatic<string>( "LoadImageAtPath", Context, imagePath, temporaryImagePath, maxSize ) );
#elif !UNITY_EDITOR && UNITY_IOS
		string temporaryImagePath = TemporaryImagePath; // Must be accessed from main thread
		string loadPath = await Task.Run( () => _NativeCamera_LoadImageAtPath( imagePath, temporaryImagePath, maxSize ) );
#else
		string loadPath = imagePath;
#endif

		Texture2D result = null;

		if( !linearColorSpace )
		{
			using( UnityWebRequest www = UnityWebRequestTexture.GetTexture( "file://" + loadPath, markTextureNonReadable && !generateMipmaps ) )
			{
				UnityWebRequestAsyncOperation asyncOperation = www.SendWebRequest();
				while( !asyncOperation.isDone )
					await Task.Yield();

#if UNITY_2020_1_OR_NEWER
				if( www.result != UnityWebRequest.Result.Success )
#else
				if( www.isNetworkError || www.isHttpError )
#endif
				{
					Debug.LogWarning( "Couldn't use UnityWebRequest to load image, falling back to LoadImage: " + www.error );
				}
				else
				{
					Texture2D texture = DownloadHandlerTexture.GetContent( www );

					if( !generateMipmaps )
						result = texture;
					else
					{
						Texture2D mipmapTexture = null;
						try
						{
							// Generate a Texture with mipmaps enabled
							// Credits: https://forum.unity.com/threads/generate-mipmaps-at-runtime-for-a-texture-loaded-with-unitywebrequest.644842/#post-7571809
							NativeArray<byte> textureData = texture.GetRawTextureData<byte>();

							mipmapTexture = new Texture2D( texture.width, texture.height, texture.format, true );
#if UNITY_2019_3_OR_NEWER
							mipmapTexture.SetPixelData( textureData, 0 );
#else
							NativeArray<byte> mipmapTextureData = mipmapTexture.GetRawTextureData<byte>();
							NativeArray<byte>.Copy( textureData, mipmapTextureData, textureData.Length );
							mipmapTexture.LoadRawTextureData( mipmapTextureData );
#endif
							mipmapTexture.Apply( true, markTextureNonReadable );

							result = mipmapTexture;
						}
						catch( Exception e )
						{
							Debug.LogException( e );

							if( mipmapTexture )
								Object.DestroyImmediate( mipmapTexture );
						}
						finally
						{
							Object.DestroyImmediate( texture );
						}
					}
				}
			}
		}

		if( !result ) // Fallback to Texture2D.LoadImage if something goes wrong
		{
			string extension = Path.GetExtension( imagePath ).ToLowerInvariant();
			TextureFormat format = ( extension == ".jpg" || extension == ".jpeg" ) ? TextureFormat.RGB24 : TextureFormat.RGBA32;

			result = new Texture2D( 2, 2, format, generateMipmaps, linearColorSpace );

			try
			{
				if( !result.LoadImage( File.ReadAllBytes( loadPath ), markTextureNonReadable ) )
				{
					Debug.LogWarning( "Couldn't load image at path: " + loadPath );

					Object.DestroyImmediate( result );
					return null;
				}
			}
			catch( Exception e )
			{
				Debug.LogException( e );

				Object.DestroyImmediate( result );
				return null;
			}
			finally
			{
				if( loadPath != imagePath )
				{
					try
					{
						File.Delete( loadPath );
					}
					catch { }
				}
			}
		}

		return result;
	}
#endif

	public static Texture2D GetVideoThumbnail( string videoPath, int maxSize = -1, double captureTimeInSeconds = -1.0, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )
	{
		if( maxSize <= 0 )
			maxSize = SystemInfo.maxTextureSize;

#if !UNITY_EDITOR && UNITY_ANDROID
		string thumbnailPath = AJC.CallStatic<string>( "GetVideoThumbnail", Context, videoPath, TemporaryImagePath + ".png", false, maxSize, captureTimeInSeconds );
#elif !UNITY_EDITOR && UNITY_IOS
		string thumbnailPath = _NativeCamera_GetVideoThumbnail( videoPath, TemporaryImagePath + ".png", maxSize, captureTimeInSeconds );
#else
		string thumbnailPath = null;
#endif

		if( !string.IsNullOrEmpty( thumbnailPath ) )
			return LoadImageAtPath( thumbnailPath, maxSize, markTextureNonReadable, generateMipmaps, linearColorSpace );
		else
			return null;
	}

#if UNITY_2018_4_OR_NEWER && !NATIVE_CAMERA_DISABLE_ASYNC_FUNCTIONS
	public static async Task<Texture2D> GetVideoThumbnailAsync( string videoPath, int maxSize = -1, double captureTimeInSeconds = -1.0, bool markTextureNonReadable = true, bool generateMipmaps = true, bool linearColorSpace = false )
	{
		if( maxSize <= 0 )
			maxSize = SystemInfo.maxTextureSize;

#if !UNITY_EDITOR && UNITY_ANDROID
		string temporaryImagePath = TemporaryImagePath; // Must be accessed from main thread
		string thumbnailPath = await TryCallNativeAndroidFunctionOnSeparateThread( () => AJC.CallStatic<string>( "GetVideoThumbnail", Context, videoPath, temporaryImagePath + ".png", false, maxSize, captureTimeInSeconds ) );
#elif !UNITY_EDITOR && UNITY_IOS
		string temporaryImagePath = TemporaryImagePath; // Must be accessed from main thread
		string thumbnailPath = await Task.Run( () => _NativeCamera_GetVideoThumbnail( videoPath, temporaryImagePath + ".png", maxSize, captureTimeInSeconds ) );
#else
		string thumbnailPath = null;
#endif

		if( !string.IsNullOrEmpty( thumbnailPath ) )
			return await LoadImageAtPathAsync( thumbnailPath, maxSize, markTextureNonReadable, generateMipmaps, linearColorSpace );
		else
			return null;
	}

	private static async Task<T> TryCallNativeAndroidFunctionOnSeparateThread<T>( Func<T> function )
	{
		T result = default( T );
		bool hasResult = false;

		await Task.Run( () =>
		{
			if( AndroidJNI.AttachCurrentThread() != 0 )
				Debug.LogWarning( "Couldn't attach JNI thread, calling native function on the main thread" );
			else
			{
				try
				{
					result = function();
					hasResult = true;
				}
				finally
				{
					AndroidJNI.DetachCurrentThread();
				}
			}
		} );

		return hasResult ? result : function();
	}
#endif

	public static ImageProperties GetImageProperties( string imagePath )
	{
		if( !File.Exists( imagePath ) )
			throw new FileNotFoundException( "File not found at " + imagePath );

#if !UNITY_EDITOR && UNITY_ANDROID
		string value = AJC.CallStatic<string>( "GetImageProperties", Context, imagePath );
#elif !UNITY_EDITOR && UNITY_IOS
		string value = _NativeCamera_GetImageProperties( imagePath );
#else
		string value = null;
#endif

		int width = 0, height = 0;
		string mimeType = null;
		ImageOrientation orientation = ImageOrientation.Unknown;
		if( !string.IsNullOrEmpty( value ) )
		{
			string[] properties = value.Split( '>' );
			if( properties != null && properties.Length >= 4 )
			{
				if( !int.TryParse( properties[0].Trim(), out width ) )
					width = 0;
				if( !int.TryParse( properties[1].Trim(), out height ) )
					height = 0;

				mimeType = properties[2].Trim();
				if( mimeType.Length == 0 )
				{
					string extension = Path.GetExtension( imagePath ).ToLowerInvariant();
					if( extension == ".png" )
						mimeType = "image/png";
					else if( extension == ".jpg" || extension == ".jpeg" )
						mimeType = "image/jpeg";
					else if( extension == ".gif" )
						mimeType = "image/gif";
					else if( extension == ".bmp" )
						mimeType = "image/bmp";
					else
						mimeType = null;
				}

				int orientationInt;
				if( int.TryParse( properties[3].Trim(), out orientationInt ) )
					orientation = (ImageOrientation) orientationInt;
			}
		}

		return new ImageProperties( width, height, mimeType, orientation );
	}

	public static VideoProperties GetVideoProperties( string videoPath )
	{
		if( !File.Exists( videoPath ) )
			throw new FileNotFoundException( "File not found at " + videoPath );

#if !UNITY_EDITOR && UNITY_ANDROID
		string value = AJC.CallStatic<string>( "GetVideoProperties", Context, videoPath );
#elif !UNITY_EDITOR && UNITY_IOS
		string value = _NativeCamera_GetVideoProperties( videoPath );
#else
		string value = null;
#endif

		int width = 0, height = 0;
		long duration = 0L;
		float rotation = 0f;
		if( !string.IsNullOrEmpty( value ) )
		{
			string[] properties = value.Split( '>' );
			if( properties != null && properties.Length >= 4 )
			{
				if( !int.TryParse( properties[0].Trim(), out width ) )
					width = 0;
				if( !int.TryParse( properties[1].Trim(), out height ) )
					height = 0;
				if( !long.TryParse( properties[2].Trim(), out duration ) )
					duration = 0L;
				if( !float.TryParse( properties[3].Trim().Replace( ',', '.' ), NumberStyles.Float, CultureInfo.InvariantCulture, out rotation ) )
					rotation = 0f;
			}
		}

		if( rotation == -90f )
			rotation = 270f;

		return new VideoProperties( width, height, duration, rotation );
	}
	#endregion
}