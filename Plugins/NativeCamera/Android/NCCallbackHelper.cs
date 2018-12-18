#if !UNITY_EDITOR && UNITY_ANDROID
using UnityEngine;

namespace NativeCameraNamespace
{
	public class NCCallbackHelper : MonoBehaviour
	{
		private void Awake()
		{
			DontDestroyOnLoad( gameObject );
		}
	}
}
#endif