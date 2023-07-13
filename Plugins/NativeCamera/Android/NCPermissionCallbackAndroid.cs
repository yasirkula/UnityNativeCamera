#if UNITY_EDITOR || UNITY_ANDROID
using System.Threading;
using UnityEngine;

namespace NativeCameraNamespace
{
	public class NCPermissionCallbackAndroid : AndroidJavaProxy
	{
		private object threadLock;
		public int Result { get; private set; }

		public NCPermissionCallbackAndroid( object threadLock ) : base( "com.yasirkula.unity.NativeCameraPermissionReceiver" )
		{
			Result = -1;
			this.threadLock = threadLock;
		}

		public void OnPermissionResult( int result )
		{
			Result = result;

			lock( threadLock )
			{
				Monitor.Pulse( threadLock );
			}
		}
	}

	public class NCPermissionCallbackAsyncAndroid : AndroidJavaProxy
	{
		private readonly NativeCamera.PermissionCallback callback;
		private readonly NCCallbackHelper callbackHelper;

		public NCPermissionCallbackAsyncAndroid( NativeCamera.PermissionCallback callback ) : base( "com.yasirkula.unity.NativeCameraPermissionReceiver" )
		{
			this.callback = callback;
			callbackHelper = new GameObject( "NCCallbackHelper" ).AddComponent<NCCallbackHelper>();
		}

		public void OnPermissionResult( int result )
		{
			callbackHelper.CallOnMainThread( () => callback( (NativeCamera.Permission) result ) );
		}
	}
}
#endif