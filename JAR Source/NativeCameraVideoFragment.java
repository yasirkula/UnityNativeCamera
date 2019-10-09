package com.yasirkula.unity;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.Locale;

/**
 * Created by yasirkula on 22.04.2018.
 */

public class NativeCameraVideoFragment extends Fragment
{
	private static final int CAMERA_VIDEO_CODE = 554777;

	private static final String VIDEO_NAME = "VID_camera";
	public static final String DEFAULT_CAMERA_ID = "UNCV_DEF_CAMERA";
	public static final String AUTHORITY_ID = "UNCV_AUTHORITY";
	public static final String QUALITY_ID = "UNCV_QUALITY";
	public static final String MAX_DURATION_ID = "UNCV_DURATION";
	public static final String MAX_SIZE_ID = "UNCV_SIZE";

	public static boolean provideExtraOutputOnAndroidQ = true;

	private final NativeCameraMediaReceiver mediaReceiver;
	private String fileTargetPath;
	private int lastVideoId;

	public NativeCameraVideoFragment()
	{
		mediaReceiver = null;
	}

	public NativeCameraVideoFragment( final NativeCameraMediaReceiver mediaReceiver )
	{
		this.mediaReceiver = mediaReceiver;
	}

	@Override
	public void onCreate( Bundle savedInstanceState )
	{
		super.onCreate( savedInstanceState );
		if( mediaReceiver == null )
			getFragmentManager().beginTransaction().remove( this ).commit();
		else
		{
			int defaultCamera = getArguments().getInt( DEFAULT_CAMERA_ID );
			String authority = getArguments().getString( AUTHORITY_ID );
			int quality = getArguments().getInt( QUALITY_ID );
			int maxDuration = getArguments().getInt( MAX_DURATION_ID );
			long maxSize = getArguments().getLong( MAX_SIZE_ID );

			// Credit: https://stackoverflow.com/a/8555925/2373034
			// Get the id of the newest video in the Gallery
			Cursor videoCursor = null;
			try
			{
				videoCursor = getActivity().getContentResolver().query( MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
						new String[] { MediaStore.Video.Media._ID }, null, null, MediaStore.Video.Media._ID + " DESC" );
				if( videoCursor != null && videoCursor.moveToFirst() )
					lastVideoId = videoCursor.getInt( videoCursor.getColumnIndex( MediaStore.Video.Media._ID ) );
				else
					lastVideoId = Integer.MAX_VALUE;
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );
			}
			finally
			{
				if( videoCursor != null )
					videoCursor.close();
			}

			Intent intent = new Intent( MediaStore.ACTION_VIDEO_CAPTURE );

			// Setting a "EXTRA_OUTPUT" can stop the video from also appearing in the Gallery
			// but it is reported that while doing so, the camera app may stop functioning properly
			// on old devices or Nexus devices. Since we can delete any file from the Gallery on devices
			// that run on Android 9 or earlier, don't use EXTRA_OUTPUT on those devices for maximum compatibility
			// with older devices. Use EXTRA_OUTPUT on only Android 10 which restricts our access to the Gallery,
			// otherwise we can't delete the copies of the captured video on those devices
			if( provideExtraOutputOnAndroidQ && android.os.Build.VERSION.SDK_INT >= 29 && !Environment.isExternalStorageLegacy() )
			{
				File videoFile = new File( getActivity().getCacheDir(), VIDEO_NAME + ".mp4" );
				try
				{
					if( videoFile.exists() )
					{
						videoFile.delete();
						videoFile.createNewFile();
					}
				}
				catch( Exception e )
				{
					Log.e( "Unity", "Exception:", e );

					getFragmentManager().beginTransaction().remove( this ).commit();
					return;
				}

				fileTargetPath = videoFile.getAbsolutePath();
				NativeCameraUtils.SetOutputUri( getActivity(), intent, authority, videoFile );
			}
			else
				fileTargetPath = null;

			if( quality >= 0 )
				intent.putExtra( MediaStore.EXTRA_VIDEO_QUALITY, quality <= 1 ? quality : 1 );
			if( maxDuration > 0 )
				intent.putExtra( MediaStore.EXTRA_DURATION_LIMIT, maxDuration );
			if( maxSize > 0L )
				intent.putExtra( MediaStore.EXTRA_SIZE_LIMIT, maxSize );

			if( defaultCamera == 0 )
				NativeCameraUtils.SetDefaultCamera( intent, true );
			else if( defaultCamera == 1 )
				NativeCameraUtils.SetDefaultCamera( intent, false );

			if( getActivity().getPackageManager().queryIntentActivities( intent, PackageManager.MATCH_DEFAULT_ONLY ).size() > 0 )
				startActivityForResult( intent, CAMERA_VIDEO_CODE );
			else
				startActivityForResult( Intent.createChooser( intent, "" ), CAMERA_VIDEO_CODE );
		}
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data )
	{
		if( requestCode != CAMERA_VIDEO_CODE )
			return;

		File result = null;
		if( resultCode == Activity.RESULT_OK )
		{
			if( data != null )
			{
				String path = NativeCameraUtils.getPathFromURI( getActivity(), data.getData() );
				if( path != null && path.length() > 0 )
					result = new File( path );
			}

			if( result == null && fileTargetPath != null && fileTargetPath.length() > 0 )
				result = new File( fileTargetPath );

			if( lastVideoId != 0L ) // it got reset somehow?
			{
				// Credit: https://stackoverflow.com/a/8555925/2373034
				// Check if the video is saved to the Gallery
				Cursor videoCursor = null;
				try
				{
					final String[] videoColumns = { MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE, MediaStore.Video.Media._ID };
					videoCursor = getActivity().getContentResolver().query( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoColumns,
							MediaStore.Video.Media._ID + ">?", new String[] { "" + lastVideoId }, MediaStore.Video.Media._ID + " DESC" );
					while( videoCursor != null && videoCursor.moveToNext() )
					{
						String path = videoCursor.getString( videoCursor.getColumnIndex( MediaStore.Video.Media.DATA ) );
						if( path != null && path.length() > 0 )
						{
							long size = videoCursor.getLong( videoCursor.getColumnIndex( MediaStore.Video.Media.SIZE ) );
							if( result == null || !result.exists() || size == result.length() )
							{
								try
								{
									String id = "" + videoCursor.getInt( videoCursor.getColumnIndex( MediaStore.Video.Media._ID ) );
									String extension = "";
									int extensionIndex = path.lastIndexOf( '.' );
									if( extensionIndex > 0 && extensionIndex < path.length() - 1 && extensionIndex > path.lastIndexOf( File.separatorChar ) )
										extension = path.substring( extensionIndex ).toLowerCase( Locale.US );

									File copiedFile = new File( getActivity().getCacheDir(), VIDEO_NAME + extension );
									Uri contentUri;
									try
									{
										contentUri = Uri.withAppendedPath( MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id );
									}
									catch( Exception e )
									{
										Log.e( "Unity", "Exception:", e );
										contentUri = null;
									}

									NativeCameraUtils.CopyFile( getActivity(), new File( path ), copiedFile, contentUri );

									if( copiedFile.length() > 1L )
									{
										result = copiedFile;

										if( !NativeCamera.KeepGalleryReferences )
										{
											Log.d( "Unity", "Trying to delete duplicate gallery item: " + path );

											getActivity().getContentResolver().delete( MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
													MediaStore.Video.Media._ID + "=?", new String[] { id } );
										}
									}
								}
								catch( Exception e )
								{
									Log.e( "Unity", "Exception:", e );
								}

								break;
							}
						}
					}
				}
				catch( Exception e )
				{
					Log.e( "Unity", "Exception:", e );
				}
				finally
				{
					if( videoCursor != null )
						videoCursor.close();
				}
			}
		}

		if( mediaReceiver != null )
			mediaReceiver.OnMediaReceived( result != null && result.length() > 1L ? result.getAbsolutePath() : "" );

		getFragmentManager().beginTransaction().remove( this ).commit();
	}
}