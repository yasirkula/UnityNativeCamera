package com.yasirkula.unity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

/**
 * Created by yasirkula on 22.04.2018.
 */

public class NativeCamera
{
	public static boolean KeepGalleryReferences = false; // false: if camera app saves a copy of the image/video in Gallery, automatically delete it
	public static boolean QuickCapture = true; // true: the Confirm/Delete screen after the capture is skipped
	public static boolean UseDefaultCameraApp = true; // false: Intent.createChooser is used to pick the camera app

	public static boolean HasCamera( Context context )
	{
		PackageManager pm = context.getPackageManager();
		return pm.hasSystemFeature( PackageManager.FEATURE_CAMERA ) || pm.hasSystemFeature( PackageManager.FEATURE_CAMERA_FRONT );
	}

	public static void TakePicture( Context context, NativeCameraMediaReceiver mediaReceiver, int defaultCamera )
	{
		if( !CanAccessCamera( context, mediaReceiver ) )
			return;

		Bundle bundle = new Bundle();
		bundle.putInt( NativeCameraPictureFragment.DEFAULT_CAMERA_ID, defaultCamera );
		bundle.putString( NativeCameraPictureFragment.AUTHORITY_ID, NativeCameraUtils.GetAuthority( context ) );

		final Fragment request = new NativeCameraPictureFragment( mediaReceiver );
		request.setArguments( bundle );

		( (Activity) context ).getFragmentManager().beginTransaction().add( 0, request ).commit();
	}

	public static void RecordVideo( Context context, NativeCameraMediaReceiver mediaReceiver, int defaultCamera, int quality, int maxDuration, long maxSize )
	{
		if( !CanAccessCamera( context, mediaReceiver ) )
			return;

		Bundle bundle = new Bundle();
		bundle.putInt( NativeCameraVideoFragment.DEFAULT_CAMERA_ID, defaultCamera );
		bundle.putString( NativeCameraVideoFragment.AUTHORITY_ID, NativeCameraUtils.GetAuthority( context ) );
		bundle.putInt( NativeCameraVideoFragment.QUALITY_ID, quality );
		bundle.putInt( NativeCameraVideoFragment.MAX_DURATION_ID, maxDuration );
		bundle.putLong( NativeCameraVideoFragment.MAX_SIZE_ID, maxSize );

		final Fragment request = new NativeCameraVideoFragment( mediaReceiver );
		request.setArguments( bundle );

		( (Activity) context ).getFragmentManager().beginTransaction().add( 0, request ).commit();
	}

	// Credit: https://stackoverflow.com/a/35456817/2373034
	public static void OpenSettings( Context context )
	{
		Uri uri = Uri.fromParts( "package", context.getPackageName(), null );

		Intent intent = new Intent();
		intent.setAction( Settings.ACTION_APPLICATION_DETAILS_SETTINGS );
		intent.setData( uri );

		context.startActivity( intent );
	}

	@TargetApi( Build.VERSION_CODES.M )
	public static int CheckPermission( Context context )
	{
		if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M )
			return 1;

		if( context.checkSelfPermission( Manifest.permission.WRITE_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED ||
				context.checkSelfPermission( Manifest.permission.READ_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED )
			return 0;

		// Credit: https://blog.egorand.me/taking-photos-not-so-simply-how-i-got-bitten-by-action_image_capture/
		return !NativeCameraUtils.IsPermissionDefinedInManifest( context, Manifest.permission.CAMERA ) ||
				context.checkSelfPermission( Manifest.permission.CAMERA ) == PackageManager.PERMISSION_GRANTED ? 1 : 0;
	}

	// Credit: https://github.com/Over17/UnityAndroidPermissions/blob/0dca33e40628f1f279decb67d901fd444b409cd7/src/UnityAndroidPermissions/src/main/java/com/unity3d/plugin/UnityAndroidPermissions.java
	public static void RequestPermission( Context context, final NativeCameraPermissionReceiver permissionReceiver, final int lastCheckResult )
	{
		if( CheckPermission( context ) == 1 )
		{
			permissionReceiver.OnPermissionResult( 1 );
			return;
		}

		if( lastCheckResult == 0 ) // If user clicked "Don't ask again" before, don't bother asking them again
		{
			permissionReceiver.OnPermissionResult( 0 );
			return;
		}

		final Fragment request = new NativeCameraPermissionFragment( permissionReceiver );
		( (Activity) context ).getFragmentManager().beginTransaction().add( 0, request ).commit();
	}

	public static String LoadImageAtPath( Context context, String path, final String temporaryFilePath, final int maxSize )
	{
		return NativeCameraUtils.LoadImageAtPath( context, path, temporaryFilePath, maxSize );
	}

	public static String GetImageProperties( Context context, final String path )
	{
		return NativeCameraUtils.GetImageProperties( context, path );
	}

	public static String GetVideoProperties( Context context, final String path )
	{
		return NativeCameraUtils.GetVideoProperties( context, path );
	}

	public static String GetVideoThumbnail( Context context, final String path, final String savePath, final boolean saveAsJpeg, final int maxSize, final double captureTime )
	{
		return NativeCameraUtils.GetVideoThumbnail( context, path, savePath, saveAsJpeg, maxSize, captureTime );
	}

	private static boolean CanAccessCamera( Context context, NativeCameraMediaReceiver mediaReceiver )
	{
		if( !HasCamera( context ) )
		{
			Log.e( "Unity", "Device has no registered cameras!" );

			mediaReceiver.OnMediaReceived( "" );
			return false;
		}

		if( CheckPermission( context ) != 1 )
		{
			Log.e( "Unity", "Can't access camera, permission denied!" );

			mediaReceiver.OnMediaReceived( "" );
			return false;
		}

		if( NativeCameraUtils.GetAuthority( context ) == null )
		{
			Log.e( "Unity", "Can't find ContentProvider, camera is inaccessible!" );

			mediaReceiver.OnMediaReceived( "" );
			return false;
		}

		return true;
	}
}