#if !UNITY_EDITOR && UNITY_ANDROID
using System.Threading;
using UnityEngine;

namespace NativeCameraNamespace
{
	public class NCCameraCallbackAndroid : AndroidJavaProxy
	{
		private object threadLock;
		public string Path { get; private set; }

		public NCCameraCallbackAndroid( object threadLock ) : base( "com.yasirkula.unity.NativeCameraMediaReceiver" )
		{
			Path = string.Empty;
			this.threadLock = threadLock;
		}

		public void OnMediaReceived( string path )
		{
			Path = path;

			lock( threadLock )
			{
				Monitor.Pulse( threadLock );
			}
		}
	}
}
#endif