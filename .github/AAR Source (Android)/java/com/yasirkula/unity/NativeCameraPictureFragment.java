package com.yasirkula.unity;

import android.app.Activity;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

/**
 * Created by yasirkula on 22.04.2018.
 */

public class NativeCameraPictureFragment extends Fragment
{
	private static final int CAMERA_PICTURE_CODE = 554776;

	private static final String IMAGE_NAME = "IMG_camera.jpg";
	public static final String DEFAULT_CAMERA_ID = "UNCP_DEF_CAMERA";
	public static final String AUTHORITY_ID = "UNCP_AUTHORITY";

	private final NativeCameraMediaReceiver mediaReceiver;
	private String fileTargetPath;
	private int lastImageId = Integer.MAX_VALUE;

	public NativeCameraPictureFragment()
	{
		mediaReceiver = null;
	}

	public NativeCameraPictureFragment( final NativeCameraMediaReceiver mediaReceiver )
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

			File photoFile = new File( getActivity().getCacheDir(), IMAGE_NAME );
			try
			{
				if( photoFile.exists() )
					NativeCameraUtils.ClearFileContents( photoFile );
				else
					photoFile.createNewFile();
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );
				onActivityResult( CAMERA_PICTURE_CODE, Activity.RESULT_CANCELED, null );
				return;
			}

			fileTargetPath = photoFile.getAbsolutePath();

			// Credit: https://stackoverflow.com/a/8555925/2373034
			// Get the id of the newest image in the Gallery
			Cursor imageCursor = null;
			try
			{
				imageCursor = getActivity().getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
						new String[] { MediaStore.Images.Media._ID }, null, null, MediaStore.Images.Media._ID + " DESC" );
				if( imageCursor != null )
				{
					if( imageCursor.moveToFirst() )
						lastImageId = imageCursor.getInt( imageCursor.getColumnIndex( MediaStore.Images.Media._ID ) );
					else if( imageCursor.getCount() <= 0 )
					{
						// If there are currently no images in the Gallery, after the image is captured, querying the Gallery with
						// "_ID > lastImageId" will return the newly captured image since its ID will always be greater than Integer.MIN_VALUE
						lastImageId = Integer.MIN_VALUE;
					}
				}
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );
			}
			finally
			{
				if( imageCursor != null )
					imageCursor.close();
			}

			Intent intent = new Intent( MediaStore.ACTION_IMAGE_CAPTURE );
			NativeCameraUtils.SetOutputUri( getActivity(), intent, authority, photoFile );

			if( defaultCamera == 0 )
				NativeCameraUtils.SetDefaultCamera( intent, true );
			else if( defaultCamera == 1 )
				NativeCameraUtils.SetDefaultCamera( intent, false );

			if( NativeCamera.QuickCapture )
				intent.putExtra( "android.intent.extra.quickCapture", true );

			try
			{
				//  MIUI devices have issues with Intent.createChooser on at least Android 11 (https://stackoverflow.com/questions/67785661/taking-and-picking-photos-on-poco-x3-with-android-11-does-not-work)
				if( NativeCamera.UseDefaultCameraApp || ( Build.VERSION.SDK_INT == 30 && NativeCameraUtils.IsXiaomiOrMIUI() ) )
					startActivityForResult( intent, CAMERA_PICTURE_CODE );
				else
					startActivityForResult( Intent.createChooser( intent, "" ), CAMERA_PICTURE_CODE );
			}
			catch( ActivityNotFoundException e )
			{
				Toast.makeText( getActivity(), "No apps can perform this action.", Toast.LENGTH_LONG ).show();
				onActivityResult( CAMERA_PICTURE_CODE, Activity.RESULT_CANCELED, null );
			}
		}
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data )
	{
		if( requestCode != CAMERA_PICTURE_CODE )
			return;

		File result = null;
		if( resultCode == Activity.RESULT_OK )
		{
			result = new File( fileTargetPath );

			if( lastImageId != 0L ) // it got reset somehow?
			{
				// Credit: https://stackoverflow.com/a/8555925/2373034
				// Check if the image is saved to the Gallery instead of the specified path
				Cursor imageCursor = null;
				try
				{
					final String[] imageColumns = { MediaStore.Images.Media.DATA, MediaStore.Images.Media.SIZE, MediaStore.Images.Media._ID };
					imageCursor = getActivity().getContentResolver().query( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageColumns,
							MediaStore.Images.Media._ID + ">?", new String[] { "" + lastImageId }, MediaStore.Images.Media._ID + " DESC" );
					if( imageCursor != null && imageCursor.moveToNext() )
					{
						String path = imageCursor.getString( imageCursor.getColumnIndex( MediaStore.Images.Media.DATA ) );
						if( path != null && path.length() > 0 )
						{
							boolean shouldDeleteImage = false;

							String id = "" + imageCursor.getInt( imageCursor.getColumnIndex( MediaStore.Images.Media._ID ) );
							long size = imageCursor.getLong( imageCursor.getColumnIndex( MediaStore.Images.Media.SIZE ) );
							if( size > result.length() )
							{
								shouldDeleteImage = true;

								Uri contentUri;
								try
								{
									contentUri = Uri.withAppendedPath( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id );
								}
								catch( Exception e )
								{
									Log.e( "Unity", "Exception:", e );
									contentUri = null;
								}

								NativeCameraUtils.CopyFile( getActivity(), new File( path ), result, contentUri );
							}
							else
							{
								try
								{
									if( !new File( path ).getCanonicalPath().equals( result.getCanonicalPath() ) )
										shouldDeleteImage = true;
								}
								catch( Exception e )
								{
									Log.e( "Unity", "Exception:", e );
								}
							}

							if( shouldDeleteImage && !NativeCamera.KeepGalleryReferences )
							{
								Log.d( "Unity", "Trying to delete duplicate gallery item: " + path );

								getActivity().getContentResolver().delete( MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
										MediaStore.Images.Media._ID + "=?", new String[] { id } );
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
					if( imageCursor != null )
						imageCursor.close();
				}
			}
		}

		if( mediaReceiver != null )
			mediaReceiver.OnMediaReceived( result != null && result.length() > 1L ? result.getAbsolutePath() : "" );

		getFragmentManager().beginTransaction().remove( this ).commit();
	}
}