#if UNITY_EDITOR || UNITY_ANDROID
using UnityEngine;

namespace NativeCameraNamespace
{
	public class NCPermissionCallbackAndroid : AndroidJavaProxy
	{
		private readonly NativeCamera.PermissionCallback callback;
		private readonly NCCallbackHelper callbackHelper;

		public NCPermissionCallbackAndroid( NativeCamera.PermissionCallback callback ) : base( "com.yasirkula.unity.NativeCameraPermissionReceiver" )
		{
			this.callback = callback;
			callbackHelper = NCCallbackHelper.Create( true );
		}

		[UnityEngine.Scripting.Preserve]
		public void OnPermissionResult( int result )
		{
			callbackHelper.CallOnMainThread( () => callback( (NativeCamera.Permission) result ) );
		}
	}
}
#endif