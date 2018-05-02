#if !UNITY_EDITOR && UNITY_IOS
using UnityEngine;

namespace NativeCameraNamespace
{
	public class NCCameraCallbackiOS : MonoBehaviour
	{
		private static NCCameraCallbackiOS instance;
		private NativeCamera.CameraCallback callback;

		private float nextBusyCheckTime;

		public static bool IsBusy { get; private set; }

		[System.Runtime.InteropServices.DllImport( "__Internal" )]
		private static extern int _NativeCamera_IsCameraBusy();

		public static void Initialize( NativeCamera.CameraCallback callback )
		{
			if( IsBusy )
				return;

			if( instance == null )
			{
				instance = new GameObject( "NCCameraCallbackiOS" ).AddComponent<NCCameraCallbackiOS>();
				DontDestroyOnLoad( instance.gameObject );
			}

			instance.callback = callback;

			instance.nextBusyCheckTime = Time.realtimeSinceStartup + 1f;
			IsBusy = true;
		}

		private void Update()
		{
			if( IsBusy )
			{
				if( Time.realtimeSinceStartup >= nextBusyCheckTime )
				{
					nextBusyCheckTime = Time.realtimeSinceStartup + 1f;

					if( _NativeCamera_IsCameraBusy() == 0 )
					{
						if( callback != null )
						{
							callback( null );
							callback = null;
						}

						IsBusy = false;
					}
				}
			}
		}

		public void OnMediaReceived( string path )
		{
			if( string.IsNullOrEmpty( path ) )
				path = null;

			if( callback != null )
			{
				callback( path );
				callback = null;
			}

			IsBusy = false;
		}
	}
}
#endif