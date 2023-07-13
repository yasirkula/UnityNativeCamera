#if UNITY_EDITOR || UNITY_ANDROID
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
			callbackHelper.CallOnMainThread( () => callback( !string.IsNullOrEmpty( path ) ? path : null ) );
		}
	}
}
#endif