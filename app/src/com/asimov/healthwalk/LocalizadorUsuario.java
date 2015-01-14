package com.asimov.healthwalk;

import java.util.ArrayList;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

/**
 * Contiene la funcionalidad de localizacion del usuario en el mapa
 * @author Oscar Gonzalez Ossorio
 * @author Alejandro Lopez Espinosa
 */
public class LocalizadorUsuario extends Service 
								implements com.google.android.gms.location.LocationListener, 
										   ConnectionCallbacks, 
										   OnConnectionFailedListener, 
										   com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks
{
  


	private final Context mContext;

    // Atributos para determinar el proveedor de localizacion
    private boolean loc_activada = false;
    private boolean gps_activado = false;
    private boolean red_activada = false;
    
    // Actividad que contiene el mapa que utiliza las localizaciones obtenidas
    private MapActivity map;
    
	// Indica si los servicios de Google Play estan activados
    protected boolean serviciosActivados;
    
    // Lista de observadores
    private ArrayList<ObservadorLocalizaciones> observadores;
    
    // Gestor de ubicaciones
    protected LocationManager loc_manager;

    // Objeto que almacena los ajustes de localizacion
    protected LocationRequest loc_request;
    
    // Cliente de la API de Google
    protected GoogleApiClient google_API_client;

    /**
     * Constructor sin argumentos (no se usa, necesario para la correccion del codigo)
     */
    public LocalizadorUsuario(){
    	mContext = getApplicationContext();
    }
    
    /**
     * Constructor de la clase LocalizadorUsuario.
     * Se comprueba la validez de los servicios de Google del dispositivo y se inicializan
     * los atributos de la clase.
     * @param context Contexto de la actividad MapActivity
     */
    public LocalizadorUsuario(Context context) {
    	map = (MapActivity) context;
    	mContext = context;
    	serviciosActivados = servicesConnected();
    	observadores = new ArrayList<ObservadorLocalizaciones>();
    	loc_manager = (LocationManager) mContext.getSystemService(LOCATION_SERVICE);
		google_API_client = new GoogleApiClient.Builder(mContext)
                                        .addApi(LocationServices.API)
                                        .addConnectionCallbacks(this)
                                        .addOnConnectionFailedListener(this)
                                        .build();
		
		loc_request = LocationRequest.create();
		loc_request.setFastestInterval(Utils.getTiempoMinimo());
		loc_request.setInterval(Utils.getTiempoMinimo() * 3).setPriority(Utils.getPrioridad());
		loc_request.setSmallestDisplacement(Utils.getDistanciaMinima());
    }

    
    /**
     * Se ejecuta cuando existe una ubicacion mas actualizada disponible
     * @param location La ultima localizacion actualizada del usuario.
     */
    @Override
    public void onLocationChanged(Location location) {
    	if(esMejorLocalizacion(location, map.localizacion_actual)){
    		for(ObservadorLocalizaciones observador : observadores){
    			observador = (ObservadorLocalizaciones) mContext;
    			observador.cambioLocalizacion(location);
    		}
    	}
    }
    

    /**
     * Determina si la nueva localizacion obtenida es mejor que la actual
     * 
     * @param nueva_localizacion La nueva localizacion obtenida del cliente de localizaciones
     * @param localizacion_actual La localizacion actual
     *            
     * @return "true" si la nueva localizacion es mejor que la actual y "false" en caso contrario
     */
    public static boolean esMejorLocalizacion(Location nueva_localizacion, Location localizacion_actual) {
        if (localizacion_actual == null) {
            // Si la localizacion actual es nula, la nueva es mejor
            return true;
        }

        // Comprobacion de las marcas de tiempo de las dos localizaciones
        long tiempoDelta = nueva_localizacion.getTime() - localizacion_actual.getTime();
        boolean esMuchoMasReciente = tiempoDelta > Utils.MARCO_TIEMPO;
        boolean esMuchoMasAntigua = tiempoDelta < - Utils.MARCO_TIEMPO;
        boolean esMasReciente = tiempoDelta > 0;

        // Si ha pasado mas de un minuto desde la obtencion de la localizacion actual 
        // la nueva es mejor porque el usuario se puede haber desplazado
        if (esMuchoMasReciente) {
            return true;
            // Si la nueva localizacion tiene una antigüedad de mas de un minuto
            // es peor que la actual
        } else if (esMuchoMasAntigua) {
            return false;
        }

        // Comprobacion de la precision de las localizaciones
        int precisionDelta = (int) (nueva_localizacion.getAccuracy() - localizacion_actual.getAccuracy());
        boolean esMenosPrecisa = precisionDelta > 0;
        boolean esMasPrecisa = precisionDelta < 0;
        boolean esMuchoMenosPrecisa = precisionDelta > 200;

        // Comprobacion de que la localizacion nueva y la antigua son del mismo 
        // proveedor de localizaciones
        boolean sonDelMismoProveedor = esElMismoProveedor(nueva_localizacion.getProvider(), localizacion_actual.getProvider());

        // Se determina la aceptacion de la nueva localizacion usando una combinacion de
        // las marcas de tiempo y las precisiones
        if (esMasPrecisa) {
            return true;
        } else if (esMasReciente && !esMenosPrecisa) {
            return true;
        } else if (esMasReciente && !esMuchoMenosPrecisa && sonDelMismoProveedor) {
            return true;
        }
        return false;
    }

    /**
     * Comprueba si los dos proveedores de localizacion son iguales
     * 
     * @param proveedor1 proveedor
     * @param proveedor2 proveedor
     * 
     * @return "true" si los dos proveedores son iguales y "false" en caso contrario
     */
    public static boolean esElMismoProveedor(String proveedor1, String proveedor2) {
        if (proveedor1 == null) {
            return proveedor2 == null;
        }
        return proveedor1.equals(proveedor2);
    }
    
    /**
     * Registro del observador para ser notificado cuando la localizacion cambia
     * @param observador Observador que se quiere registrar frente a los cambios de estado
     */
    public void registrarObservador(ObservadorLocalizaciones observador){
    	observadores.add(observador);
    }
    
    /**
     * Eliminacion del observador de la lista de observadores cuando ya no es necesario
     * @param observador
     */
    public void eliminarObservador(ObservadorLocalizaciones observador){
    	observadores.remove(observador);
    }
 
    /**
     * Sobreescrito para implementar todos los metodos de Service
     */
    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
    
    
    /**
     * Gestion de los errores al conectarse al cliente de localizaciones de Google
     * @param connectionResult Devuelve el resultado de la conexion con el cliente de localizaciones
     */
	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		switch(connectionResult.getErrorCode()){
			case ConnectionResult.SUCCESS:
				break;
			default:
				Toast.makeText(mContext, "Se ha producido un error mientras se establecia conexion con los servicios de Google", Toast.LENGTH_LONG).show();
				break;
		}
	}

	/**
	 * Proceso de conexion a los servicios de ubicacion de Google, en el que se
	 * solicitan actualizaciones de ubicacion
	 * @param arg0
	 */
	@Override
	public void onConnected(Bundle arg0) {
		Log.d("onConnected()","onConnected()");
		
	    gps_activado = false;
	    try {
	    gps_activado = loc_manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
	    } catch (Exception ex) {
	    	ex.printStackTrace();
	    }

	    red_activada = false;
	    try {
	      red_activada = loc_manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
	    } catch (Exception ex) {
	    	ex.printStackTrace();
	    }

	    loc_activada = gps_activado || red_activada;

	    if (!loc_activada) {
	     Toast.makeText(mContext, "Debe activar los servicios de ubicacion para que la aplicacion funcione", Toast.LENGTH_LONG).show();
	    } else {
			LocationServices.FusedLocationApi.requestLocationUpdates(
	        google_API_client, loc_request, this);
	    }
	}
	
	/**
	 * Peticion de actualizaciones de localizacion
	 */
	protected void solicitarActualizaciones(){
		if(!google_API_client.isConnected())
			google_API_client.connect();
	}
	
	/**
	 * Detencion de las actualizaciones de localizacion
	 */
	protected void pararActualizaciones(){
		if(google_API_client.isConnected()){
			LocationServices.FusedLocationApi.removeLocationUpdates(google_API_client, this);	
			google_API_client.disconnect();
		}
		
	}
	
	/**
	 * Sobrescritos para implementar todos los metodos de ConnectionCallback
	 */
	@Override
	public void onConnectionSuspended(int arg0) {
	}
	
	
	/**
	 * Sobrescritos para implementar todos los metodos de ConnectionCallback
	 */
	@Override
	public void onDisconnected() {
	}
	
	/**
	 * Validacion de los servicios de Google presentes en el dispositivo.
	 * En el caso de que no esten presentes, se muestra un mensaje de error.
	 * @return "true" si existen servicios de Google disponibles o "false" en caso contrario
	 */
	private boolean servicesConnected() {
		// Comprueba que los servicios de Google Play esten disponibles
		int resultCode =
				GooglePlayServicesUtil.
				isGooglePlayServicesAvailable(map);
		// Si los servicios de Google Play estan disponibles
		if (ConnectionResult.SUCCESS == resultCode) {
			// Escritura en el log para depuracion
			Log.d(Utils.ASIMOV,
					"Los servicios de Google Play estan disponibles.");
			// Confirmacion de que los servicios estan disponibles
			return true;
			// Los servicios de Google Play no estan disponibles por alguna razon.
		} else {
			Log.d(Utils.ASIMOV,"Los servicios de Google Play NO estan disponibles.");
			int errorCode = new ConnectionResult(resultCode, PendingIntent.getActivity(map.getApplicationContext(), 
												resultCode, map.getIntent(), PendingIntent.FLAG_NO_CREATE)).getErrorCode();
			Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
					 errorCode,
					 map,
					 Utils.CODIGO_ERROR_SIN_SERVICIOS_GOOGLE);
	
			 // Si los servicios de Google Play pueden proporcionar un dialogo de error
			 if (errorDialog != null) {
				 ErrorDialogFragment errorFragment = new ErrorDialogFragment();
				 errorFragment.setDialog(errorDialog);
				 errorFragment.show(map.getFragmentManager(), mContext.getString(R.string.errorSinServicios));
			 }
			return false;
		}
	}

	/**
	 * Define un DialogFragment que muestra el mensaje de error
	 */
    public static class ErrorDialogFragment extends DialogFragment {
        // Dialogo de error
        private Dialog mDialog;

        /**
         * Constructor por defecto
         */
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }
        
        /**
         * Asigna el dialogo de error a un nuevo dialogo
         * @param dialog
         */
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
        
        
        /**
         * Permite construir un dialogo personalizado en vez del basico
         */
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
        
    }
	
}
	
