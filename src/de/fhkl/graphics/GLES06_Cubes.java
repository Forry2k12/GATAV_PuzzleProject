// an OpenGL ES example 

package de.fhkl.graphics;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import my.pack.graphics.primitives.Cube;
import my.pack.graphics.primitives.Cylinder;
import my.pack.graphics.primitives.Disk;
import my.pack.graphics.primitives.Sphere;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.os.Bundle;
import android.util.FloatMath;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class GLES06_Cubes extends Activity {
    private GLSurfaceView touchableGLSurfaceView;

    private final int MENU_RESET = 1, MENU_PAN = 2, MENU_ZOOM = 3;
    private final int GROUP_DEFAULT = 0, GROUP_PAN = 1, GROUP_ZOOM = 2;
    private boolean PAN = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	
		touchableGLSurfaceView = new TouchableGLSurfaceView(this);
		setContentView(touchableGLSurfaceView);
		touchableGLSurfaceView.setFocusableInTouchMode(true);
		touchableGLSurfaceView.requestFocus();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(GROUP_DEFAULT, MENU_RESET, 0, "Reset");
		menu.add(GROUP_PAN, MENU_PAN, 0, "Pan");
		menu.add(GROUP_ZOOM, MENU_ZOOM, 0, "Zoom");
		return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
		if (PAN) {
		    menu.setGroupVisible(GROUP_PAN, false);
		    menu.setGroupVisible(GROUP_ZOOM, true);
		} else {
		    menu.setGroupVisible(GROUP_PAN, true);
		    menu.setGroupVisible(GROUP_ZOOM, false);
		}
		return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_RESET:
			    TouchableGLSurfaceView.resetViewing();
			    Toast.makeText(this, "trackball reset", Toast.LENGTH_SHORT).show();
			    touchableGLSurfaceView.requestRender();
			    return true;
			case MENU_PAN:
			    Toast.makeText(this, "panning activated", Toast.LENGTH_SHORT).show();
			    PAN = true;
			    TouchableGLSurfaceView.guiZoom = false;
			    return true;
			case MENU_ZOOM:
			    Toast.makeText(this, "zooming activated", Toast.LENGTH_SHORT).show();
			    PAN = false;
			    TouchableGLSurfaceView.guiZoom = true;
			    return true;
		}
		return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
		super.onResume();
		touchableGLSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
		super.onPause();
		touchableGLSurfaceView.onPause();
    }
}

// touchable GLSurfaceView with
// an implementation of a virtual trackball rotation control
class TouchableGLSurfaceView extends GLSurfaceView {
    private OurRenderer ourRenderer;

    static public boolean guiZoom = true;
    // possible touch states
    final static int NONE = 0;
    final static int ROTATE = 1;
    final static int ZOOM = 2;
    final static int PAN = 3;
    int touchState = NONE;

    final static float MIN_DIST = 50;
    static int oldDistance = 0;
    static int centerX = 0, centerY = 0;
    static int oldCenterX = 0, oldCenterY = 0;

    static float EYE_DISTANCE, EYE_DISTANCE_INC;
    static float PAN_X, PAN_Y, PAN_INC;
    static float CURRENT_QUATERNION[], LAST_QUATERNION[];
    static float TRANSFORM_MATRIX[];

    static int OLD_MOUSE_X, OLD_MOUSE_Y, MOUSE_BUTTON_PRESSED;

    static int WINDOW_W = 600;
    static int WINDOW_H = 800;

    static float zNear = 1.0f, zFar = 1000.0f;

    static {
		CURRENT_QUATERNION = new float[4];
		LAST_QUATERNION = new float[4];
		TRANSFORM_MATRIX = new float[16];
    }

    public TouchableGLSurfaceView(Context context) {
		super(context);
		ourRenderer = new OurRenderer();
		setRenderer(ourRenderer);
		
		ourRenderer.autoRotate=false;
		setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
		float x = event.getX();
		float y = event.getY();
	
		float p1x, p1y, p2x, p2y;
		// normalize mouse positions
		p1x = (2.0f * OLD_MOUSE_X - WINDOW_W) / WINDOW_W;
		p1y = (WINDOW_H - 2.0f * OLD_MOUSE_Y) / WINDOW_H;
		p2x = (2.0f * x - WINDOW_W) / WINDOW_W;
		p2y = (WINDOW_H - 2.0f * y) / WINDOW_H;
	
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
			    touchState = ROTATE;
			    OLD_MOUSE_X = (int) x;
			    OLD_MOUSE_Y = (int) y;
			    break;
			case MotionEvent.ACTION_POINTER_DOWN:
			    // secondary touch event starts: remember distance
			    oldDistance = (int) calcDistance(event);
			    // and midpoint
			    calcMidpoint(event);
			    oldCenterX = centerX;
			    oldCenterY = centerY;
			    if (oldDistance > MIN_DIST) {
					if (guiZoom) {
					    touchState = ZOOM;
					} else {
					    touchState = PAN;
					}
			    }
			    break;
			case MotionEvent.ACTION_MOVE:  
			    if (touchState == ROTATE) {
					// single finger rotate
					//Trackball.trackball(LAST_QUATERNION, p1x, p1y, p2x, p2y);
					OLD_MOUSE_X = (int) x;
					OLD_MOUSE_Y = (int) y;
					//Trackball.add_quats(LAST_QUATERNION, CURRENT_QUATERNION, CURRENT_QUATERNION);
					requestRender();
			    } else if (touchState == ZOOM) {
					// double-finger zoom, zoom depends on changing distance
					int dist = (int) calcDistance(event);
					if (dist > MIN_DIST) {
					    if (dist > oldDistance)
						EYE_DISTANCE -= EYE_DISTANCE_INC;
					    else if (dist < oldDistance)
						EYE_DISTANCE += EYE_DISTANCE_INC;
					    oldDistance = dist;
					    requestRender();
					}
			    } else if (touchState == PAN) {
					int dist = (int) calcDistance(event);
					calcMidpoint(event);
					if (dist > MIN_DIST) {
					    if (centerX > oldCenterX)
						PAN_X -= PAN_INC;
					    if (centerX < oldCenterX)
						PAN_X += PAN_INC;
					    if (centerY > oldCenterY)
						PAN_Y += PAN_INC;
					    if (centerY < oldCenterY)
						PAN_Y -= PAN_INC;
					    oldCenterX = centerX;
					    oldCenterY = centerY;
					    requestRender();
					}
			    }
			    break;
			case MotionEvent.ACTION_UP:
			    touchState = NONE;
			    break;
			case MotionEvent.ACTION_POINTER_UP:
			    touchState = ROTATE;
			    // update touch down location for drag event to holding finger
			    switch (event.getActionIndex()) {
				    case 0:
						OLD_MOUSE_X = (int) event.getX(1);
						OLD_MOUSE_Y = (int) event.getY(1);
						break;
				    case 1:
						OLD_MOUSE_X = (int) event.getX(0);
						OLD_MOUSE_Y = (int) event.getY(0);
						break;
			    }
			    break;
		}
		return true;
    }

    private float calcDistance(MotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return FloatMath.sqrt(x * x + y * y);
    }

    private void calcMidpoint(MotionEvent event) {
		centerX = (int) ((event.getX(0) + event.getX(1)) / 2);
		centerY = (int) ((event.getY(0) + event.getY(1)) / 2);
    }

    // the implementation of the renderer interface
	private class OurRenderer implements GLSurfaceView.Renderer {
	
		private Cube cube;
		private Cube cube2;
		private Cube cube3;
		private Cube cube4;
		private Cube cube5;
		private Cube cube6;
		private Cube cube7;
		private Cube cube8;
	
		private long milSecPerRotation=20*1000;
		private double angle=0.0f;
		private long lastTime;
		
		public boolean autoRotate=true;
	
		public OurRenderer() {
		    // cube centered among origin with length size of each edge
		    // size: length of each edge
		    cube = new Cube(2.0f);	
		    cube2 = new Cube(2.0f);
		    cube3 = new Cube(2.0f);
		    cube4 = new Cube(2.0f);   
		    cube5 = new Cube(2.0f);	    
		    cube6 = new Cube(2.0f);
		    cube7 = new Cube(2.0f);
		    cube8 = new Cube(2.0f);
		}
	
		public void onDrawFrame(GL10 gl) {
		    // the first thing to do: clear screen and depth buffer
		    gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
	
		    // reset modelview matrix
		    gl.glMatrixMode(GL10.GL_MODELVIEW);
		    gl.glLoadIdentity();
	
		    // set ambient light color
		    float model_ambient[] = { 0.5f, 0.5f, 0.5f, 1.0f };
		    ByteBuffer bb1 = ByteBuffer.allocateDirect(model_ambient.length * 4);
		    bb1.order(ByteOrder.nativeOrder());
		    FloatBuffer fb1 = bb1.asFloatBuffer();
		    fb1.put(model_ambient);
		    fb1.position(0);
		    gl.glLightModelfv(GL10.GL_LIGHT_MODEL_AMBIENT, fb1);
	
		    // set light position of LIGHT0
		    float light_position[] = { 1.0f, 1.0f, 1.0f, 0.0f };
		    ByteBuffer bb2 = ByteBuffer.allocateDirect(light_position.length * 4);
		    bb2.order(ByteOrder.nativeOrder());
		    FloatBuffer fb2 = bb2.asFloatBuffer();
		    fb2.put(light_position);
		    fb2.position(0);
		    gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_POSITION, fb2);
	
		    // enable ligth and lighting
		    gl.glEnable(GL10.GL_LIGHT0);
		    gl.glEnable(GL10.GL_LIGHTING);
	
		    // manipulate modelview matrix by setting viewing transformation
		    gl.glTranslatef(-PAN_X, -PAN_Y, -EYE_DISTANCE);
		    
	// muss drin bleiben, so bewegt sich das teil aber nicht
		    Trackball.build_rotmatrix(TRANSFORM_MATRIX, CURRENT_QUATERNION);
	// ++++++++++++++++++++++++++++++++++++++++++++++++++
		    
		    gl.glMultMatrixf(TRANSFORM_MATRIX, 0);
	
		    gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		    gl.glEnableClientState(GL10.GL_NORMAL_ARRAY);
		    // gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
		    gl.glColor4x(65536, 0, 0, 65536);
		    
		    // create an automatic rotation
		    long time = System.currentTimeMillis();
		    long deltaTime = time-lastTime;
		    lastTime=time;
		    if (autoRotate) {
				angle = (float) (angle + 360.0 / milSecPerRotation * deltaTime);
				if (angle > 360.0f)
				    angle -= 360;
				// Log.e("LOGTIME", deltaTime+" "+(360.0/milSecPerRotation*deltaTime)+" "+angle);	
		    }  
		   // gl.glRotatef(90, x, y, 0);
	
		    gl.glPushMatrix();
		    	gl.glTranslatef(-2.2f, 1.1f, 0.0f);
		    	cube.draw(gl);
		    gl.glPopMatrix();
		    gl.glPushMatrix();
		    	gl.glTranslatef(-2.2f, -1.1f, 0.0f);
		    	cube2.draw(gl);
		    gl.glPopMatrix();
		    gl.glPushMatrix();
		    	gl.glTranslatef(0.0f, 1.1f, 0.0f);
		    	cube3.draw(gl);
		    gl.glPopMatrix();
		    gl.glPushMatrix();
		    	gl.glTranslatef(0.0f, -1.1f, 0.0f);
		    	cube4.draw(gl);
		    gl.glPopMatrix();
		    gl.glPushMatrix();
		    	gl.glTranslatef(2.2f, 1.1f, 0.0f);
		    	cube5.draw(gl);
		    gl.glPopMatrix();
		    gl.glPushMatrix();
		    	gl.glTranslatef(2.2f, -1.1f, 0.0f);
		    	cube6.draw(gl);
		    gl.glPopMatrix();
		    
		    
		    gl.glPushMatrix();
	    		gl.glTranslatef(2.2f, 3.3f, 0.0f);	    	
	    		cube7.draw(gl);
	    	gl.glPopMatrix();
		    
	    	gl.glPushMatrix();
	    		gl.glTranslatef(0.0f, 3.3f, 0.0f);	    	
	    		cube8.draw(gl);
	    	gl.glPopMatrix();
		}
	
		// resize of viewport
		// set projection matrix
		public void onSurfaceChanged(GL10 gl, int width, int height) {
		    gl.glViewport(0, 0, width, height);
	
		    float aspectRatio = (float) width / height;
		    gl.glMatrixMode(GL10.GL_PROJECTION);
		    gl.glLoadIdentity();
		    GLU.gluPerspective(gl, 45.0f, aspectRatio, zNear, zFar);
		    GLU.gluLookAt(gl, 0.0f, 0.0f, 5.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
		    gl.glMatrixMode(GL10.GL_MODELVIEW);
		    
		    lastTime = System.currentTimeMillis();
		}
	
		// creation of viewport
		// initialization of some opengl features
		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
	
		    gl.glDisable(GL10.GL_DITHER);
		    gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);
	
		    gl.glClearColor(1, 0, 0, 1);
		    gl.glEnable(GL10.GL_CULL_FACE);
		    gl.glShadeModel(GL10.GL_FLAT);
		    //gl.glShadeModel(GL10.GL_SMOOTH);
		    gl.glEnable(GL10.GL_DEPTH_TEST);
	
		    resetViewing();
		}
    }

    // reset of view parameters
    static void resetViewing() {
		EYE_DISTANCE = 14.0f;
		EYE_DISTANCE_INC = 0.5f;
		PAN_X = 0.0f;
		PAN_Y = 0.0f;
		PAN_INC = 0.1f;
	
		// trackball init
		//Trackball.trackball(CURRENT_QUATERNION, 0.0f, 0.0f, 0.0f, 0.0f);
    }
}
