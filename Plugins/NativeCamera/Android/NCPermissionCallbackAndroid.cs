#if !UNITY_EDITOR && UNITY_ANDROID
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
}
#endif