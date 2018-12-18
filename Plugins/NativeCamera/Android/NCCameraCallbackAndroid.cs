#if !UNITY_EDITOR && UNITY_ANDROID
using System.Collections;
using UnityEngine;

namespace NativeCameraNamespace
{
	public class NCCameraCallbackAndroid : AndroidJavaProxy
	{
		private NativeCamera.CameraCallback callback;

		public NCCameraCallbackAndroid( NativeCamera.CameraCallback callback ) : base( "com.yasirkula.unity.NativeCameraMediaReceiver" )
		{
			this.callback = callback;
		}

		public void OnMediaReceived( string path )
		{
			NCCallbackHelper coroutineHolder = new GameObject( "NCCallbackHelper" ).AddComponent<NCCallbackHelper>();
			coroutineHolder.StartCoroutine( MediaReceiveCoroutine( coroutineHolder.gameObject, path ) );
		}

		private IEnumerator MediaReceiveCoroutine( GameObject obj, string path )
		{
			yield return null;

			if( string.IsNullOrEmpty( path ) )
				path = null;

			try
			{
				if( callback != null )
					callback( path );
			}
			finally
			{
				Object.Destroy( obj );
			}
		}
	}
}
#endif