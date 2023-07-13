#if UNITY_EDITOR || UNITY_IOS
using UnityEngine;

namespace NativeCameraNamespace
{
	public class NCPermissionCallbackiOS : MonoBehaviour
	{
		private static NCPermissionCallbackiOS instance;
		private NativeCamera.PermissionCallback callback;

		public static void Initialize( NativeCamera.PermissionCallback callback )
		{
			if( instance == null )
			{
				instance = new GameObject( "NCPermissionCallbackiOS" ).AddComponent<NCPermissionCallbackiOS>();
				DontDestroyOnLoad( instance.gameObject );
			}
			else if( instance.callback != null )
				instance.callback( NativeCamera.Permission.ShouldAsk );

			instance.callback = callback;
		}

		public void OnPermissionRequested( string message )
		{
			NativeCamera.PermissionCallback _callback = callback;
			callback = null;

			if( _callback != null )
				_callback( (NativeCamera.Permission) int.Parse( message ) );
		}
	}
}
#endif