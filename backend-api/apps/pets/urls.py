from rest_framework.routers import SimpleRouter

from django.urls import path

from .views import PetPhotoView, PetViewSet

router = SimpleRouter()
router.register("", PetViewSet, basename="pet")

urlpatterns = [
    path("<uuid:pk>/photo/", PetPhotoView.as_view(), name="pet-photo"),
    *router.urls,
]
