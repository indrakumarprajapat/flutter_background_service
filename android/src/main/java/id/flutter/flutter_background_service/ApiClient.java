package id.flutter.flutter_background_service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.PUT;
import retrofit2.http.Query;
import id.flutter.flutter_background_service.DriverLocation;

public class ApiClient {

//    void updateDriverLocation(Context context,String driver_id,DriverLocation driverLocation, double lat, double lng) {
//        /*Create handle for the RetrofitInstance interface*/
//        String token = BackgroundService.getApiTokenValue(context);
//
//        if (token != null && token.length() > 10) {
//            ApiEndpoints apiEndpoints = RetrofitClientInstance.getRetrofitInstance(token).create(ApiEndpoints.class);
//            Call<DriverLocation> call = apiEndpoints.updateDriverLocation(
//                    new DriverLocation(),
//                    lat,
//                    lng,
//                    driver_id);
//
//            call.enqueue(new Callback<DriverLocation>() {
//                @Override
//                public void onResponse(Call<DriverLocation> call, Response<DriverLocation> response) {
//                    Log.d(">>> ApiCall-Success >", response.toString());
//                }
//
//                @Override
//                public void onFailure(Call<DriverLocation> call, Throwable t) {
//                    Log.d(">>> ApiCall-Failed > ", t.getMessage());
//                }
//            });
//        }
//    }

    void addAppEventLog() {

    }

//    void rejectRideRequest(RejectRideRequest rejectRideRequest){
//
//    }
//
//    void passRideRequest(PassRideRequest passRideRequest){
//
//    }

}
