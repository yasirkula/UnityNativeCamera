#if UNITY_IOS
using UnityEditor;
using UnityEditor.Callbacks;
using System.IO;
using UnityEditor.iOS.Xcode;
#endif

public class NCPostProcessBuild
{
	private const bool ENABLED = true;

	private const string CAMERA_USAGE_DESCRIPTION = "Capture media with camera";
	private const string MICROPHONE_USAGE_DESCRIPTION = "Capture microphone input in videos";

#if UNITY_IOS
#pragma warning disable 0162
	[PostProcessBuild]
	public static void OnPostprocessBuild( BuildTarget target, string buildPath )
	{
		if( !ENABLED )
			return;

		if( target == BuildTarget.iOS )
		{
			string pbxProjectPath = PBXProject.GetPBXProjectPath( buildPath );
			string plistPath = Path.Combine( buildPath, "Info.plist" );

			PBXProject pbxProject = new PBXProject();
			pbxProject.ReadFromFile( pbxProjectPath );

			string targetGUID = pbxProject.TargetGuidByName( PBXProject.GetUnityTargetName() );

			pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework MobileCoreServices" );
			pbxProject.AddBuildProperty( targetGUID, "OTHER_LDFLAGS", "-framework ImageIO" );

			File.WriteAllText( pbxProjectPath, pbxProject.WriteToString() );

			PlistDocument plist = new PlistDocument();
			plist.ReadFromString( File.ReadAllText( plistPath ) );

			PlistElementDict rootDict = plist.root;
			rootDict.SetString( "NSCameraUsageDescription", CAMERA_USAGE_DESCRIPTION );
			rootDict.SetString( "NSMicrophoneUsageDescription", MICROPHONE_USAGE_DESCRIPTION );

			File.WriteAllText( plistPath, plist.WriteToString() );
		}
	}
#pragma warning restore 0162
#endif
}