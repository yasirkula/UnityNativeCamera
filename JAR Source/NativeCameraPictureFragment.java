package com.yasirkula.unity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;

/**
 * Created by yasirkula on 22.04.2018.
 */

public class NativeCameraPictureFragment extends Fragment
{
	private static final int CAMERA_PICTURE_CODE = 554776;

	private static final String IMAGE_NAME = "IMG_camera.jpg";
	public static final String AUTHORITY_ID = "UNCP_AUTHORITY";

	private final NativeCameraMediaReceiver mediaReceiver;
	private String fileTargetPath;
	private int lastImageId;

	public NativeCameraPictureFragment() { mediaReceiver = null; }

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
			String authority = getArguments().getString( AUTHORITY_ID );

			File photoFile = new File( getActivity().getCacheDir(), IMAGE_NAME );
			try
			{
				if( photoFile.exists() )
				{
					photoFile.delete();
					photoFile.createNewFile();
				}
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );

				getFragmentManager().beginTransaction().remove( this ).commit();
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
				if( imageCursor != null && imageCursor.moveToFirst() )
					lastImageId = imageCursor.getInt( imageCursor.getColumnIndex( MediaStore.Images.Media._ID ) );
				else
					lastImageId = Integer.MAX_VALUE;
			}
			finally
			{
				if( imageCursor != null )
					imageCursor.close();
			}

			Intent intent = new Intent( MediaStore.ACTION_IMAGE_CAPTURE );
			SetOutputUri( intent, authority, photoFile );

			if( getActivity().getPackageManager().queryIntentActivities( intent, PackageManager.MATCH_DEFAULT_ONLY ).size() > 0 )
				startActivityForResult( intent, CAMERA_PICTURE_CODE );
			else
				startActivityForResult( Intent.createChooser( intent, "" ), CAMERA_PICTURE_CODE );
		}
	}

	@TargetApi( Build.VERSION_CODES.JELLY_BEAN )
	private void SetOutputUri( Intent intent, String authority, File output )
	{
		Uri photoURI;
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN )
			photoURI = NativeCameraContentProvider.getUriForFile( getActivity(), authority, output );
		else
			photoURI = Uri.fromFile( output );

		intent.putExtra( MediaStore.EXTRA_OUTPUT, photoURI );

		// Credit: https://medium.com/@quiro91/sharing-files-through-intents-part-2-fixing-the-permissions-before-lollipop-ceb9bb0eec3a
		if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN )
		{
			intent.setClipData( ClipData.newRawUri( "", photoURI ) );
			intent.setFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION );
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
							long size = imageCursor.getLong( imageCursor.getColumnIndex( MediaStore.Images.Media.SIZE ) );
							if( size > result.length() )
							{
								shouldDeleteImage = true;

								try
								{
									NativeCameraUtils.CopyFile( new File( path ), result );
								}
								catch( Exception e )
								{
									Log.e( "Unity", "Exception:", e );
								}
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
								Log.d( "Unity", "Deleting gallery item: " + path );

								String id = "" + imageCursor.getInt( imageCursor.getColumnIndex( MediaStore.Images.Media._ID ) );
								getActivity().getContentResolver().delete( MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
										MediaStore.Images.Media._ID + "=?", new String[] { id } );
							}
						}
					}
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
