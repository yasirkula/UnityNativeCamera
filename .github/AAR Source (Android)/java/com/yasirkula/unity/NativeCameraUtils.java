package com.yasirkula.unity;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * Created by yasirkula on 30.04.2018.
 */

public class NativeCameraUtils
{
	private static String authority = null;
	private static String secondaryStoragePath = null;
	private static int isXiaomiOrMIUI = 0; // 1: true, -1: false

	public static String GetAuthority( Context context )
	{
		if( authority == null )
		{
			// Find the authority of ContentProvider first
			// Credit: https://stackoverflow.com/a/2001769/2373034
			try
			{
				PackageInfo packageInfo = context.getPackageManager().getPackageInfo( context.getPackageName(), PackageManager.GET_PROVIDERS );
				ProviderInfo[] providers = packageInfo.providers;
				if( providers != null )
				{
					for( ProviderInfo provider : providers )
					{
						if( provider.name != null && provider.packageName != null && provider.authority != null &&
								provider.name.equals( NativeCameraContentProvider.class.getName() ) && provider.packageName.equals( context.getPackageName() )
								&& provider.authority.length() > 0 )
						{
							authority = provider.authority;
							break;
						}
					}
				}
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );
			}
		}

		return authority;
	}

	public static boolean IsXiaomiOrMIUI()
	{
		if( isXiaomiOrMIUI > 0 )
			return true;
		else if( isXiaomiOrMIUI < 0 )
			return false;

		if( "xiaomi".equalsIgnoreCase( android.os.Build.MANUFACTURER ) )
		{
			isXiaomiOrMIUI = 1;
			return true;
		}

		// Check if device is using MIUI
		// Credit: https://gist.github.com/Muyangmin/e8ec1002c930d8df3df46b306d03315d
		String line;
		BufferedReader inputStream = null;
		try
		{
			java.lang.Process process = Runtime.getRuntime().exec( "getprop ro.miui.ui.version.name" );
			inputStream = new BufferedReader( new InputStreamReader( process.getInputStream() ), 1024 );
			line = inputStream.readLine();

			if( line != null && line.length() > 0 )
			{
				isXiaomiOrMIUI = 1;
				return true;
			}
			else
			{
				isXiaomiOrMIUI = -1;
				return false;
			}
		}
		catch( Exception e )
		{
			isXiaomiOrMIUI = -1;
			return false;
		}
		finally
		{
			if( inputStream != null )
			{
				try
				{
					inputStream.close();
				}
				catch( Exception e )
				{
				}
			}
		}
	}

	// Credit: https://github.com/jamesmontemagno/MediaPlugin/issues/307#issuecomment-356199135
	public static void SetDefaultCamera( Intent intent, boolean useRearCamera )
	{
		if( useRearCamera )
		{
			intent.putExtra( "android.intent.extras.LENS_FACING_BACK", 1 );
			intent.putExtra( "android.intent.extras.CAMERA_FACING", 0 );
			intent.putExtra( "android.intent.extra.USE_FRONT_CAMERA", false );
		}
		else
		{
			intent.putExtra( "android.intent.extras.LENS_FACING_FRONT", 1 );
			intent.putExtra( "android.intent.extras.CAMERA_FACING", 1 );
			intent.putExtra( "android.intent.extra.USE_FRONT_CAMERA", true );
		}
	}

	@TargetApi( Build.VERSION_CODES.JELLY_BEAN )
	public static void SetOutputUri( Context context, Intent intent, String authority, File output )
	{
		Uri uri;
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN )
			uri = NativeCameraContentProvider.getUriForFile( context, authority, output );
		else
			uri = Uri.fromFile( output );

		intent.putExtra( MediaStore.EXTRA_OUTPUT, uri );

		// Credit: https://medium.com/@quiro91/sharing-files-through-intents-part-2-fixing-the-permissions-before-lollipop-ceb9bb0eec3a
		if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN )
		{
			intent.setClipData( ClipData.newRawUri( "", uri ) );
			intent.setFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION );
		}
	}

	// Credit: https://stackoverflow.com/a/6994668/2373034
	public static void ClearFileContents( File file ) throws IOException
	{
		RandomAccessFile stream = null;
		try
		{
			stream = new RandomAccessFile( file, "rw" );
			stream.setLength( 0 );
		}
		finally
		{
			if( stream != null )
			{
				try
				{
					stream.close();
				}
				catch( Exception e )
				{
				}
			}
		}
	}

	// Credit: https://stackoverflow.com/a/9293885/2373034
	public static void CopyFile( Context context, File src, File dst, Uri rawUri )
	{
		try
		{
			if( !src.exists() )
				return;

			if( dst.exists() )
				ClearFileContents( dst );

			InputStream in = new FileInputStream( src );
			try
			{
				OutputStream out = new FileOutputStream( dst );
				try
				{
					byte[] buf = new byte[1024];
					int len;
					while( ( len = in.read( buf ) ) > 0 )
						out.write( buf, 0, len );
				}
				finally
				{
					out.close();
				}
			}
			finally
			{
				in.close();
			}
		}
		catch( Exception e )
		{
			if( rawUri == null )
				Log.e( "Unity", "Exception:", e );
			else
			{
				// Try to save the file via contentResolver (can happen e.g. on Android 10 where raw file system access is restricted)
				try
				{
					InputStream input = context.getContentResolver().openInputStream( rawUri );
					if( input == null )
						return;

					OutputStream output = null;
					try
					{
						output = new FileOutputStream( dst, false );

						byte[] buf = new byte[4096];
						int len;
						while( ( len = input.read( buf ) ) > 0 )
							output.write( buf, 0, len );
					}
					finally
					{
						if( output != null )
							output.close();

						input.close();
					}
				}
				catch( Exception e2 )
				{
					Log.e( "Unity", "Exception:", e );
					Log.e( "Unity", "Exception2:", e2 );
				}
			}
		}
	}

	public static boolean IsPermissionDefinedInManifest( Context context, String permission )
	{
		try
		{
			String[] requestedPermissions = context.getPackageManager().getPackageInfo( context.getPackageName(), PackageManager.GET_PERMISSIONS ).requestedPermissions;
			if( requestedPermissions != null )
			{
				for( String requestedPermission : requestedPermissions )
				{
					if( permission.equals( requestedPermission ) )
						return true;
				}
			}
		}
		catch( PackageManager.NameNotFoundException e )
		{
		}

		return false;
	}

	// Credit: https://stackoverflow.com/a/36714242/2373034
	public static String getPathFromURI( Context context, Uri uri )
	{
		if( uri == null )
			return null;

		Log.d( "Unity", "Media uri: " + uri.toString() );

		String selection = null;
		String[] selectionArgs = null;

		try
		{
			if( Build.VERSION.SDK_INT >= 19 && DocumentsContract.isDocumentUri( context.getApplicationContext(), uri ) )
			{
				if( "com.android.externalstorage.documents".equals( uri.getAuthority() ) )
				{
					final String docId = DocumentsContract.getDocumentId( uri );
					final String[] split = docId.split( ":" );

					if( "primary".equalsIgnoreCase( split[0] ) )
						return Environment.getExternalStorageDirectory() + File.separator + split[1];
					else if( "raw".equalsIgnoreCase( split[0] ) ) // https://stackoverflow.com/a/51874578/2373034
						return split[1];

					return getSecondaryStoragePathFor( split[1] );
				}
				else if( "com.android.providers.downloads.documents".equals( uri.getAuthority() ) )
				{
					final String id = DocumentsContract.getDocumentId( uri );
					if( id.startsWith( "raw:" ) ) // https://stackoverflow.com/a/51874578/2373034
						return id.substring( 4 );

					uri = ContentUris.withAppendedId( Uri.parse( "content://downloads/public_downloads" ), Long.valueOf( id ) );
				}
				else if( "com.android.providers.media.documents".equals( uri.getAuthority() ) )
				{
					final String docId = DocumentsContract.getDocumentId( uri );
					final String[] split = docId.split( ":" );
					final String type = split[0];
					if( "image".equals( type ) )
						uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
					else if( "video".equals( type ) )
						uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
					else if( "audio".equals( type ) )
						uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
					else if( "raw".equals( type ) ) // https://stackoverflow.com/a/51874578/2373034
						return split[1];

					selection = "_id=?";
					selectionArgs = new String[] { split[1] };
				}
			}

			if( "content".equalsIgnoreCase( uri.getScheme() ) )
			{
				String[] projection = { MediaStore.Images.Media.DATA };
				Cursor cursor = null;

				try
				{
					cursor = context.getContentResolver().query( uri, projection, selection, selectionArgs, null );
					if( cursor != null )
					{
						int column_index = cursor.getColumnIndexOrThrow( MediaStore.Images.Media.DATA );
						if( cursor.moveToFirst() )
						{
							String columnValue = cursor.getString( column_index );
							if( columnValue != null && columnValue.length() > 0 )
								return columnValue;
						}
					}
				}
				catch( Exception e )
				{
				}
				finally
				{
					if( cursor != null )
						cursor.close();
				}
			}
			else if( "file".equalsIgnoreCase( uri.getScheme() ) )
				return uri.getPath();

			// File path couldn't be determined
			return null;
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return null;
		}
	}

	private static String getSecondaryStoragePathFor( String localPath )
	{
		if( secondaryStoragePath == null )
		{
			String primaryPath = Environment.getExternalStorageDirectory().getAbsolutePath();

			// Try paths saved at system environments
			// Credit: https://stackoverflow.com/a/32088396/2373034
			String strSDCardPath = System.getenv( "SECONDARY_STORAGE" );
			if( strSDCardPath == null || strSDCardPath.length() == 0 )
				strSDCardPath = System.getenv( "EXTERNAL_SDCARD_STORAGE" );

			if( strSDCardPath != null && strSDCardPath.length() > 0 )
			{
				if( !strSDCardPath.contains( ":" ) )
					strSDCardPath += ":";

				String[] externalPaths = strSDCardPath.split( ":" );
				for( int i = 0; i < externalPaths.length; i++ )
				{
					String path = externalPaths[i];
					if( path != null && path.length() > 0 )
					{
						File file = new File( path );
						if( file.exists() && file.isDirectory() && file.canRead() && !file.getAbsolutePath().equalsIgnoreCase( primaryPath ) )
						{
							String absolutePath = file.getAbsolutePath() + File.separator + localPath;
							if( new File( absolutePath ).exists() )
							{
								secondaryStoragePath = file.getAbsolutePath();
								return absolutePath;
							}
						}
					}
				}
			}

			// Try most common possible paths
			// Credit: https://gist.github.com/PauloLuan/4bcecc086095bce28e22
			String[] possibleRoots = new String[] { "/storage", "/mnt", "/storage/removable",
					"/removable", "/data", "/mnt/media_rw", "/mnt/sdcard0" };
			for( String root : possibleRoots )
			{
				try
				{
					File fileList[] = new File( root ).listFiles();
					for( File file : fileList )
					{
						if( file.exists() && file.isDirectory() && file.canRead() && !file.getAbsolutePath().equalsIgnoreCase( primaryPath ) )
						{
							String absolutePath = file.getAbsolutePath() + File.separator + localPath;
							if( new File( absolutePath ).exists() )
							{
								secondaryStoragePath = file.getAbsolutePath();
								return absolutePath;
							}
						}
					}
				}
				catch( Exception e )
				{
				}
			}

			secondaryStoragePath = "_NulL_";
		}
		else if( !secondaryStoragePath.equals( "_NulL_" ) )
			return secondaryStoragePath + File.separator + localPath;

		return null;
	}

	private static BitmapFactory.Options GetImageMetadata( final String path )
	{
		try
		{
			BitmapFactory.Options result = new BitmapFactory.Options();
			result.inJustDecodeBounds = true;
			BitmapFactory.decodeFile( path, result );

			return result;
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return null;
		}
	}

	// Credit: https://stackoverflow.com/a/30572852/2373034
	private static int GetImageOrientation( Context context, final String path )
	{
		try
		{
			ExifInterface exif = new ExifInterface( path );
			int orientationEXIF = exif.getAttributeInt( ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED );
			if( orientationEXIF != ExifInterface.ORIENTATION_UNDEFINED )
				return orientationEXIF;
		}
		catch( Exception e )
		{
		}

		Cursor cursor = null;
		try
		{
			cursor = context.getContentResolver().query( Uri.fromFile( new File( path ) ), new String[] { MediaStore.Images.Media.ORIENTATION }, null, null, null );
			if( cursor != null && cursor.moveToFirst() )
			{
				int orientation = cursor.getInt( cursor.getColumnIndex( MediaStore.Images.Media.ORIENTATION ) );
				if( orientation == 90 )
					return ExifInterface.ORIENTATION_ROTATE_90;
				if( orientation == 180 )
					return ExifInterface.ORIENTATION_ROTATE_180;
				if( orientation == 270 )
					return ExifInterface.ORIENTATION_ROTATE_270;

				return ExifInterface.ORIENTATION_NORMAL;
			}
		}
		catch( Exception e )
		{
		}
		finally
		{
			if( cursor != null )
				cursor.close();
		}

		return ExifInterface.ORIENTATION_UNDEFINED;
	}

	// Credit: https://gist.github.com/aviadmini/4be34097dfdb842ae066fae48501ed41
	private static Matrix GetImageOrientationCorrectionMatrix( final int orientation, final float scale )
	{
		Matrix matrix = new Matrix();

		switch( orientation )
		{
			case ExifInterface.ORIENTATION_ROTATE_270:
			{
				matrix.postRotate( 270 );
				matrix.postScale( scale, scale );

				break;
			}
			case ExifInterface.ORIENTATION_ROTATE_180:
			{
				matrix.postRotate( 180 );
				matrix.postScale( scale, scale );

				break;
			}
			case ExifInterface.ORIENTATION_ROTATE_90:
			{
				matrix.postRotate( 90 );
				matrix.postScale( scale, scale );

				break;
			}
			case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
			{
				matrix.postScale( -scale, scale );
				break;
			}
			case ExifInterface.ORIENTATION_FLIP_VERTICAL:
			{
				matrix.postScale( scale, -scale );
				break;
			}
			case ExifInterface.ORIENTATION_TRANSPOSE:
			{
				matrix.postRotate( 90 );
				matrix.postScale( -scale, scale );

				break;
			}
			case ExifInterface.ORIENTATION_TRANSVERSE:
			{
				matrix.postRotate( 270 );
				matrix.postScale( -scale, scale );

				break;
			}
			default:
			{
				matrix.postScale( scale, scale );
				break;
			}
		}

		return matrix;
	}

	public static String LoadImageAtPath( Context context, String path, final String temporaryFilePath, final int maxSize )
	{
		BitmapFactory.Options metadata = GetImageMetadata( path );
		if( metadata == null )
			return path;

		boolean shouldCreateNewBitmap = false;
		if( metadata.outWidth > maxSize || metadata.outHeight > maxSize )
			shouldCreateNewBitmap = true;

		if( metadata.outMimeType != null && !metadata.outMimeType.equals( "image/jpeg" ) && !metadata.outMimeType.equals( "image/png" ) )
			shouldCreateNewBitmap = true;

		int orientation = GetImageOrientation( context, path );
		if( orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED )
			shouldCreateNewBitmap = true;

		if( shouldCreateNewBitmap )
		{
			Bitmap bitmap = null;
			FileOutputStream out = null;

			try
			{
				// Credit: https://developer.android.com/topic/performance/graphics/load-bitmap.html
				int sampleSize = 1;
				int halfHeight = metadata.outHeight / 2;
				int halfWidth = metadata.outWidth / 2;
				while( ( halfHeight / sampleSize ) >= maxSize || ( halfWidth / sampleSize ) >= maxSize )
					sampleSize *= 2;

				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = sampleSize;
				options.inJustDecodeBounds = false;
				bitmap = BitmapFactory.decodeFile( path, options );

				float scaleX = 1f, scaleY = 1f;
				if( bitmap.getWidth() > maxSize )
					scaleX = maxSize / (float) bitmap.getWidth();
				if( bitmap.getHeight() > maxSize )
					scaleY = maxSize / (float) bitmap.getHeight();

				// Create a new bitmap if it should be scaled down or if its orientation is wrong
				float scale = scaleX < scaleY ? scaleX : scaleY;
				if( scale < 1f || ( orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED ) )
				{
					Matrix transformationMatrix = GetImageOrientationCorrectionMatrix( orientation, scale );
					Bitmap transformedBitmap = Bitmap.createBitmap( bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transformationMatrix, true );
					if( transformedBitmap != bitmap )
					{
						bitmap.recycle();
						bitmap = transformedBitmap;
					}
				}

				out = new FileOutputStream( temporaryFilePath );
				if( metadata.outMimeType == null || !metadata.outMimeType.equals( "image/jpeg" ) )
					bitmap.compress( Bitmap.CompressFormat.PNG, 100, out );
				else
					bitmap.compress( Bitmap.CompressFormat.JPEG, 100, out );

				path = temporaryFilePath;
			}
			catch( Exception e )
			{
				Log.e( "Unity", "Exception:", e );

				try
				{
					File temporaryFile = new File( temporaryFilePath );
					if( temporaryFile.exists() )
						temporaryFile.delete();
				}
				catch( Exception e2 )
				{
				}
			}
			finally
			{
				if( bitmap != null )
					bitmap.recycle();

				try
				{
					if( out != null )
						out.close();
				}
				catch( Exception e )
				{
				}
			}
		}

		return path;
	}

	public static String GetImageProperties( Context context, final String path )
	{
		BitmapFactory.Options metadata = GetImageMetadata( path );
		if( metadata == null )
			return "";

		int width = metadata.outWidth;
		int height = metadata.outHeight;

		String mimeType = metadata.outMimeType;
		if( mimeType == null )
			mimeType = "";

		int orientationUnity;
		int orientation = GetImageOrientation( context, path );
		if( orientation == ExifInterface.ORIENTATION_UNDEFINED )
			orientationUnity = -1;
		else if( orientation == ExifInterface.ORIENTATION_NORMAL )
			orientationUnity = 0;
		else if( orientation == ExifInterface.ORIENTATION_ROTATE_90 )
			orientationUnity = 1;
		else if( orientation == ExifInterface.ORIENTATION_ROTATE_180 )
			orientationUnity = 2;
		else if( orientation == ExifInterface.ORIENTATION_ROTATE_270 )
			orientationUnity = 3;
		else if( orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL )
			orientationUnity = 4;
		else if( orientation == ExifInterface.ORIENTATION_TRANSPOSE )
			orientationUnity = 5;
		else if( orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL )
			orientationUnity = 6;
		else if( orientation == ExifInterface.ORIENTATION_TRANSVERSE )
			orientationUnity = 7;
		else
			orientationUnity = -1;

		if( orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
				orientation == ExifInterface.ORIENTATION_TRANSPOSE || orientation == ExifInterface.ORIENTATION_TRANSVERSE )
		{
			int temp = width;
			width = height;
			height = temp;
		}

		return width + ">" + height + ">" + mimeType + ">" + orientationUnity;
	}

	@TargetApi( Build.VERSION_CODES.JELLY_BEAN_MR1 )
	public static String GetVideoProperties( Context context, final String path )
	{
		MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
		try
		{
			metadataRetriever.setDataSource( path );

			String width = metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH );
			String height = metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT );
			String duration = metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_DURATION );
			String rotation = "0";
			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 )
				rotation = metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION );

			if( width == null )
				width = "0";
			if( height == null )
				height = "0";
			if( duration == null )
				duration = "0";
			if( rotation == null )
				rotation = "0";

			return width + ">" + height + ">" + duration + ">" + rotation;
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return "";
		}
		finally
		{
			metadataRetriever.release();
		}
	}

	@TargetApi( Build.VERSION_CODES.Q )
	public static String GetVideoThumbnail( Context context, final String path, final String savePath, final boolean saveAsJpeg, int maxSize, double captureTime )
	{
		Bitmap bitmap = null;
		FileOutputStream out = null;

		try
		{
			if( captureTime < 0.0 && maxSize <= 1024 )
			{
				try
				{
					if( Build.VERSION.SDK_INT < Build.VERSION_CODES.Q )
						bitmap = ThumbnailUtils.createVideoThumbnail( path, maxSize > 512 ? MediaStore.Video.Thumbnails.FULL_SCREEN_KIND : MediaStore.Video.Thumbnails.MINI_KIND );
					else
						bitmap = ThumbnailUtils.createVideoThumbnail( new File( path ), maxSize > 512 ? new Size( 1024, 786 ) : new Size( 512, 384 ), null );
				}
				catch( Exception e )
				{
					Log.e( "Unity", "Exception:", e );
				}
			}

			if( bitmap == null )
			{
				MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
				try
				{
					metadataRetriever.setDataSource( path );

					try
					{
						int width = Integer.parseInt( metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH ) );
						int height = Integer.parseInt( metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT ) );
						if( maxSize > width && maxSize > height )
							maxSize = width > height ? width : height;
					}
					catch( Exception e )
					{
					}

					if( captureTime < 0.0 )
						captureTime = 0.0;
					else
					{
						try
						{
							double duration = Long.parseLong( metadataRetriever.extractMetadata( MediaMetadataRetriever.METADATA_KEY_DURATION ) ) / 1000.0;
							if( captureTime > duration )
								captureTime = duration;
						}
						catch( Exception e )
						{
						}
					}

					long frameTime = (long) ( captureTime * 1000000.0 );
					if( Build.VERSION.SDK_INT < 27 )
						bitmap = metadataRetriever.getFrameAtTime( frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC );
					else
						bitmap = metadataRetriever.getScaledFrameAtTime( frameTime, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize );
				}
				finally
				{
					metadataRetriever.release();
				}
			}

			if( bitmap == null )
				return "";

			out = new FileOutputStream( savePath );
			if( saveAsJpeg )
				bitmap.compress( Bitmap.CompressFormat.JPEG, 100, out );
			else
				bitmap.compress( Bitmap.CompressFormat.PNG, 100, out );

			return savePath;
		}
		catch( Exception e )
		{
			Log.e( "Unity", "Exception:", e );
			return "";
		}
		finally
		{
			if( bitmap != null )
				bitmap.recycle();

			try
			{
				if( out != null )
					out.close();
			}
			catch( Exception e )
			{
			}
		}
	}
}