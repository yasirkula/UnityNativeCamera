#if !UNITY_EDITOR && UNITY_ANDROID
using UnityEngine;

namespace NativeCameraNamespace
{
	public class NCCameraCallbackAndroid : AndroidJavaProxy
	{
		private readonly NativeCamera.CameraCallback callback;
		private readonly NCCallbackHelper callbackHelper;

		public NCCameraCallbackAndroid( NativeCamera.CameraCallback callback ) : base( "com.yasirkula.unity.NativeCameraMediaReceiver" )
		{
			this.callback = callback;
			callbackHelper = new GameObject( "NCCallbackHelper" ).AddComponent<NCCallbackHelper>();
		}

		public void OnMediaReceived( string path )
		{
			callbackHelper.CallOnMainThread( () => MediaReceiveCallback( path ) );
		}

		private void MediaReceiveCallback( string path )
		{
			if( string.IsNullOrEmpty( path ) )
				path = null;

			try
			{
				if( callback != null )
					callback( path );
			}
			finally
			{
				Object.Destroy( callbackHelper.gameObject );
			}
		}
	}
}
#endif