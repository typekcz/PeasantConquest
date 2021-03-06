package eu.kotrzena.peasantconquest.game;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.SparseArray;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedList;

import eu.kotrzena.peasantconquest.GameActivity;
import eu.kotrzena.peasantconquest.R;

public class Assets {
	private static GameActivity context;
	private static SparseArray<Bitmap> bitmaps = new SparseArray<Bitmap>();
	private static SparseArray<Bitmap> colorLayerBitmaps = new SparseArray<Bitmap>();

	// Map tileset ids to resource ids
	private static SparseArray<Integer> tilesetIds = new SparseArray<Integer>();

	private static SparseArray<Byte> tilesRoads = new SparseArray<Byte>();

	private static Typeface kenpixelFont;
	private static Paint debugPaint;
	private static Paint textPaint;
	private static Paint backgroundPaint;

	private static MediaPlayer backgroundMusic;
	private static MediaPlayer cityConqueredSound;
	private static MediaPlayer cityLostSound;

	public static void init(GameActivity context){
		Assets.context = context;
		if(bitmaps.size() == 0) {
			Field[] fields = R.drawable.class.getDeclaredFields();
			final R.drawable drawableResources = new R.drawable();
			for (Field f : fields) {
				try {
					int res_id = f.getInt(drawableResources);
					bitmaps.append(res_id, BitmapFactory.decodeResource(context.getResources(), res_id));
				} catch (IllegalAccessException e) {
					Log.w("WARN", "Assets: Not found " + f.toString());
				} catch (IllegalArgumentException e) {
					Log.w("WARN", "Assets: Not primitive field " + f.toString());
				}
			}
		}

		if(tilesetIds.size() == 0) {
			XmlPullParser xml = context.getResources().getXml(R.xml.tiles);

			try {
				int eventType = xml.getEventType();

				int tileId = -1;
				while (eventType != XmlPullParser.END_DOCUMENT) {
					switch (eventType) {
						case XmlPullParser.START_TAG:
							if (xml.getName().equals("tile")) {
								tileId = Integer.parseInt(xml.getAttributeValue(null, "id"));
								while (eventType != XmlPullParser.END_DOCUMENT) {
									eventType = xml.next();

									if (eventType == XmlPullParser.START_TAG && xml.getName().equals("property")) {
										if (xml.getAttributeValue(null, "name").equals("resource")) {
											int resId = context.getResources().getIdentifier(
													xml.getAttributeValue(null, "value"),
													"drawable",
													context.getPackageName()
											);
											if (resId != 0) {
												tilesetIds.append(tileId, resId);
												int colorResId = context.getResources().getIdentifier(
														xml.getAttributeValue(null, "value") + "_color",
														"drawable",
														context.getPackageName()
												);
												if (colorResId != 0) {
													Bitmap colorBitmap = bitmaps.get(colorResId);
													if (colorBitmap != null)
														colorLayerBitmaps.append(resId, colorBitmap);
												}
											}
										} else if (xml.getAttributeValue(null, "name").equals("roads")) {
											tilesRoads.append(tileId, Byte.parseByte(xml.getAttributeValue(null, "value")));
										}
									} else if (eventType == XmlPullParser.END_TAG && xml.getName().equals("tile")) {
										break;
									}
								}
							}
							break;
						case XmlPullParser.END_TAG:
							tileId = -1;
							break;
					}
					eventType = xml.next();
				}
			} catch (XmlPullParserException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		kenpixelFont = Typeface.createFromAsset(context.getAssets(), "fonts/kenpixel_mini_square.ttf");

		debugPaint = new Paint();
		debugPaint.setARGB(255, 255, 255, 0);
		debugPaint.setStrokeWidth(2);
		debugPaint.setStyle(Paint.Style.STROKE);

		textPaint = new Paint();
		textPaint.setARGB(255, 255, 255, 255);
		textPaint.setTextSize(15);
		textPaint.setAntiAlias(true);
		textPaint.setTypeface(kenpixelFont);

		backgroundPaint = new Paint();
		backgroundPaint.setARGB(255, 39, 174, 96);

		backgroundMusic = MediaPlayer.create(context.getApplicationContext(), R.raw.background_music);
		backgroundMusic.setLooping(true);
		cityConqueredSound = MediaPlayer.create(context.getApplicationContext(), R.raw.city_conquered);
		cityLostSound = MediaPlayer.create(context.getApplicationContext(), R.raw.city_lost);

		/*try {
			backgroundMusic.prepare();
			cityConqueredSound.prepare();
			cityLostSound.prepare();
		} catch (IOException e) {
			e.printStackTrace();
		}*/
	}

	public static Paint getDebugPaint() {
		return debugPaint;
	}

	public static Paint getTextPaint() {
		return textPaint;
	}

	public static Paint getBackgroundPaint() {
		return backgroundPaint;
	}

	public static Game loadMap(XmlPullParser xml){
		try {
			int eventType = xml.getEventType();

			Tile[][] tiles = null;
			LinkedList<Entity> entities = new LinkedList<Entity>();
			while(eventType != XmlPullParser.END_DOCUMENT){
				switch(eventType){
					case XmlPullParser.START_TAG:
						if(xml.getName().equals("map")){
							int size_x = Integer.parseInt(xml.getAttributeValue(null, "width"));
							int size_y = Integer.parseInt(xml.getAttributeValue(null, "height"));
							int mapTileSize = Integer.parseInt(xml.getAttributeValue(null, "tilewidth"));
							tiles = new Tile[size_x][size_y];
							int tilesetOffset = 0;
							int layerIndex = -1;
							while(eventType != XmlPullParser.END_DOCUMENT){
								eventType = xml.next();
								if(eventType == XmlPullParser.END_TAG && xml.getName().equals("map")){
									break;
								} else if(eventType == XmlPullParser.START_TAG && xml.getName().equals("tileset")){
									tilesetOffset = Integer.parseInt(xml.getAttributeValue(null, "firstgid"));
								} else if(eventType == XmlPullParser.START_TAG && xml.getName().equals("layer")){
									layerIndex++;
									switch(layerIndex){
										case 0: {
											int tileIndex = -1;
											while(eventType != XmlPullParser.END_DOCUMENT){
												eventType = xml.next();
												if(eventType == XmlPullParser.END_TAG && xml.getName().equals("layer")){
													break;
												}
												if(eventType == XmlPullParser.START_TAG && xml.getName().equals("tile")){
													tileIndex++;

													int x = tileIndex%size_x;
													int y = tileIndex/size_x;
													String val = xml.getAttributeValue(null, "gid");
													if(val == null)
														continue;
													int gid = Integer.parseInt(val);
													Integer intVal = tilesetIds.get(gid - tilesetOffset);
													if(intVal == null)
														continue;
													int bitmapId = intVal;
													tiles[x][y] = new Tile(x, y, getBitmap(bitmapId));

													Byte byteVal = tilesRoads.get(gid - tilesetOffset);
													if(byteVal != null)
														tiles[x][y].setRoads(tilesRoads.get(bitmapId, byteVal));
												}
											}
											break;
										}
										case 1: {
											int tileIndex = -1;
											while(eventType != XmlPullParser.END_DOCUMENT){
												eventType = xml.next();
												if(eventType == XmlPullParser.END_TAG && xml.getName().equals("layer")){
													break;
												}
												if(eventType == XmlPullParser.START_TAG && xml.getName().equals("tile")){
													tileIndex++;

													int x = tileIndex%size_x;
													int y = tileIndex/size_x;
													String val = xml.getAttributeValue(null, "gid");
													if(val == null)
														continue;
													int gid = Integer.parseInt(val);
													Integer intVal = tilesetIds.get(gid - tilesetOffset);
													if(intVal == null)
														continue;
													int bitmapId = intVal;

													switch(bitmapId){
														case R.drawable.castle:
														case R.drawable.tower: {
															PlayerCity e = new PlayerCity();
															e.setPosition(x + 0.5f, y + 1f);
															e.texture = getBitmap(bitmapId);
															e.colorLayer = getColorLayerBitmap(bitmapId);
															e.tile = tiles[x][y];
															entities.add(e);
															tiles[x][y].castle = e;
															tiles[x][y].ownerOnStart = 0;
															break;
														}
													}
												}
											}
											break;
										}
									}
								} else if(eventType == XmlPullParser.START_TAG && xml.getName().equals("objectgroup")){
									while(eventType != XmlPullParser.END_DOCUMENT){
										eventType = xml.next();
										if(eventType == XmlPullParser.END_TAG && xml.getName().equals("objectgroup")){
											break;
										}
										if(eventType == XmlPullParser.START_TAG && xml.getName().equals("object")){
											float x = Float.parseFloat(xml.getAttributeValue(null, "x"));
											float y = Float.parseFloat(xml.getAttributeValue(null, "y"));
											x /= mapTileSize;
											y /= mapTileSize;
											String gidAttr = xml.getAttributeValue(null, "gid");
											boolean loadDecorations = context.prefs.getBoolean("graphicsRenderDecorations", true);
											if(gidAttr == null){
												eventType = xml.next();
												if(eventType == XmlPullParser.END_TAG && xml.getName().equals("objectgroup"))
													break;
												if(xml.getName().equals("text")){
													eventType = xml.next();
													if(eventType == XmlPullParser.END_TAG && xml.getName().equals("objectgroup"))
														break;
													if(eventType == XmlPullParser.TEXT) {
														String text = xml.getText();
														String[] texts = text.split("\n");
														try {
															if (texts.length > 0)
																tiles[(int) x][(int) y].ownerOnStart = Integer.parseInt(texts[0]);
														} catch (NumberFormatException e) {}
														try {
															if (texts.length > 1)
																tiles[(int) x][(int) y].unitsOnStart = Float.parseFloat(texts[1]);
														} catch (NumberFormatException e) {}
													}
												}
											} else if(loadDecorations) {
												int gid = Integer.parseInt(gidAttr);
												Integer intVal = tilesetIds.get(gid - tilesetOffset);
												if(intVal == null)
													continue;
												int bitmapId = intVal;
												Entity e;
												if(bitmapId == R.drawable.mill)
													e = new Mill();
												else
													e = new Entity();
												Bitmap bitmap = getBitmap(bitmapId);
												e.setPosition(x + (((float)bitmap.getWidth())/2)/Tile.TILE_SIZE, y);
												e.texture = bitmap;
												entities.add(e);
											}
										}
									}
								}
							}
						}
						break;
					case XmlPullParser.END_TAG:
						if(xml.getName() == "map"){

						}
						break;
				}
				eventType = xml.next();
			}
			if(tiles != null)
				return new Game(context, tiles, entities);
			else
				return null;
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e){
			e.printStackTrace();
		}
		AlertDialog alertDialog = new AlertDialog.Builder(context).create();
		alertDialog.setTitle(R.string.error);
		alertDialog.setMessage(context.getString(R.string.map_load_fail));
		alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, context.getString(R.string.ok),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
		alertDialog.show();
		return null;
	}

	public static Bitmap getBitmap(int res_id){
		return bitmaps.get(res_id);
	}

	public static Bitmap getColorLayerBitmap(int res_id){
		return colorLayerBitmaps.get(res_id);
	}

	public static Bitmap getBitmap(String res_id){
		return getBitmap(context.getResources().getIdentifier(res_id, "drawable", context.getPackageName()));
	}

	public static MediaPlayer getBackgroundMusic() {
		return backgroundMusic;
	}

	public static MediaPlayer getCityConqueredSound() {
		return cityConqueredSound;
	}

	public static MediaPlayer getCityLostSound() {
		return cityLostSound;
	}
}
