package id.flutter.flutter_background_service;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.PUT;
import retrofit2.http.Query;

public interface ApiEndpoints {
    @PUT("dapi/locations")
    Call<DriverLocation> updateDriverLocation(@Body DriverLocation driverLocation, @Query("lat") String lat, @Query("lng") String lng, @Query("driver_id") String driver_id);

    @PUT("dapi/trips/reject")
    Call<DriverLocation> rejectRideRequest(@Body RejectRideRequest rejectRideRequest);

    @PUT("dapi/drivers/rides/reject")
    Call<DriverLocation> passRideRequest(@Body PassRideRequest passRideRequest);
}
