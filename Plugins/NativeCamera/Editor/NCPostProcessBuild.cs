using System.IO;
using UnityEngine;
using UnityEditor;
#if UNITY_IOS
using UnityEditor.Callbacks;
using UnityEditor.iOS.Xcode;
#endif

namespace NativeCameraNamespace
{
	[System.Serializable]
	public class Settings
	{
		private const string SAVE_PATH = "ProjectSettings/NativeCamera.json";

		public bool AutomatedSetup = true;
		public string CameraUsageDescription = "The app requires access to the camera to take pictures or record videos with it.";
		public string MicrophoneUsageDescription = "The app will capture microphone input in the recorded video.";

		private static Settings m_instance = null;
		public static Settings Instance
		{
			get
			{
				if( m_instance == null )
				{
					try
					{
						if( File.Exists( SAVE_PATH ) )
							m_instance = JsonUtility.FromJson<Settings>( File.ReadAllText( SAVE_PATH ) );
						else
							m_instance = new Settings();
					}
					catch( System.Exception e )
					{
						Debug.LogException( e );
						m_instance = new Settings();
					}
				}

				return m_instance;
			}
		}

		public void Save()
		{
			File.WriteAllText( SAVE_PATH, JsonUtility.ToJson( this, true ) );
		}

#if UNITY_2018_3_OR_NEWER
		[SettingsProvider]
		public static SettingsProvider CreatePreferencesGUI()
		{
			return new SettingsProvider( "Project/yasirkula/Native Camera", SettingsScope.Project )
			{
				guiHandler = ( searchContext ) => PreferencesGUI(),
				keywords = new System.Collections.Generic.HashSet<string>() { "Native", "Camera", "Android", "iOS" }
			};
		}
#endif

#if !UNITY_2018_3_OR_NEWER
		[PreferenceItem( "Native Camera" )]
#endif
		public static void PreferencesGUI()
		{
			EditorGUI.BeginChangeCheck();

			Instance.AutomatedSetup = EditorGUILayout.Toggle( "Automated Setup", Instance.AutomatedSetup );

			EditorGUI.BeginDisabledGroup( !Instance.AutomatedSetup );
			Instance.CameraUsageDescription = EditorGUILayout.DelayedTextField( "Camera Usage Description", Instance.CameraUsageDescription );
			Instance.MicrophoneUsageDescription = EditorGUILayout.DelayedTextField( "Microphone Usage Description", Instance.MicrophoneUsageDescription );
			EditorGUI.EndDisabledGroup();

			if( EditorGUI.EndChangeCheck() )
				Instance.Save();
		}
	}

	public class NCPostProcessBuild
	{
#if UNITY_IOS
		[PostProcessBuild]
		public static void OnPostprocessBuild( BuildTarget target, string buildPath )
		{
			if( !Settings.Instance.AutomatedSetup )
				return;

			if( target == BuildTarget.iOS )
			{
				string pbxProjectPath = PBXProject.GetPBXProjectPath( buildPath );
				string plistPath = Path.Combine( buildPath, "Info.plist" );

				PBXProject pbxProject = new PBXProject();
				pbxProject.ReadFromFile( pbxProjectPath );

#if UNITY_2019_3_OR_NEWER
				string targetGUID = pbxProject.GetUnityFrameworkTargetGuid();
#else
				string targetGUID = pbxProject.TargetGuidByName( PBXProject.GetUnityTargetName() );
#endif

				pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework MobileCoreServices" );
				pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework ImageIO" );

				File.WriteAllText( pbxProjectPath, pbxProject.WriteToString() );

				PlistDocument plist = new PlistDocument();
				plist.ReadFromString( File.ReadAllText( plistPath ) );

				PlistElementDict rootDict = plist.root;
				if( !string.IsNullOrEmpty( Settings.Instance.CameraUsageDescription ) )
					rootDict.SetString( "NSCameraUsageDescription", Settings.Instance.CameraUsageDescription );
				if( !string.IsNullOrEmpty( Settings.Instance.MicrophoneUsageDescription ) )
					rootDict.SetString( "NSMicrophoneUsageDescription", Settings.Instance.MicrophoneUsageDescription );

				File.WriteAllText( plistPath, plist.WriteToString() );
			}
		}
#endif
	}
}