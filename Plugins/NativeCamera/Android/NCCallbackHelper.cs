#if UNITY_EDITOR || UNITY_ANDROID
using System;
using UnityEngine;

namespace NativeCameraNamespace
{
	public class NCCallbackHelper : MonoBehaviour
	{
		private bool autoDestroyWithCallback;
		private Action mainThreadAction = null;

		public static NCCallbackHelper Create( bool autoDestroyWithCallback )
		{
			NCCallbackHelper result = new GameObject( "NCCallbackHelper" ).AddComponent<NCCallbackHelper>();
			result.autoDestroyWithCallback = autoDestroyWithCallback;
			DontDestroyOnLoad( result.gameObject );
			return result;
		}

		public void CallOnMainThread( Action function )
		{
			lock( this )
			{
				mainThreadAction += function;
			}
		}

		private void Update()
		{
			if( mainThreadAction != null )
			{
				try
				{
					Action temp;
					lock( this )
					{
						temp = mainThreadAction;
						mainThreadAction = null;
					}

					temp();
				}
				finally
				{
					if( autoDestroyWithCallback )
						Destroy( gameObject );
				}
			}
		}
	}
}
#endif