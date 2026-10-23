import { useEffect, useState } from "react";
import { supabase } from "../../../supabaseClient";
import "./menu_page.css";

const DISH_IMAGE_BUCKET = "Dish Images";

const dishImageUrl = (restaurantName, imagePath) => {
  if (!imagePath) return "";
  if (/^https?:\/\//i.test(imagePath)) return imagePath;

  const folderName = `${restaurantName.replaceAll("_", " ")} Dishes`;
  const storagePath = imagePath.includes("/")
    ? imagePath
    : `${folderName}/${imagePath}`;

  return supabase.storage
    .from(DISH_IMAGE_BUCKET)
    .getPublicUrl(storagePath).data.publicUrl;
};

export default function RestaurantMenu({ restaurant, onBack }) {
  const [menuItems, setMenuItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const loadMenu = async () => {
      setLoading(true);
      setError("");

      const { data, error: queryError } = await supabase
        .from("Menu_Items")
        .select("id, item_name, item_cost, item_image, item_description, availability")
        .eq("restaurant_id", restaurant.id)
        .order("item_name");

      if (queryError) {
        console.error("Error loading restaurant menu:", queryError.message);
        setError("Unable to load this restaurant's menu.");
      } else {
        setMenuItems(data);
      }

      setLoading(false);
    };

    loadMenu();
  }, [restaurant.id]);

  return (
    <>
      <button type="button" onClick={onBack}>
        Back to restaurants
      </button>
      <h3>{restaurant.restaurant_name} Menu</h3>
      {loading && <p>Loading menu...</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && !error && menuItems.length === 0 && (
        <p>No menu items available.</p>
      )}
      <section id="menu-items">
        {menuItems.map((item) => (
          <article className="menu-item" key={item.id}>
            {item.item_image && (
              <img
                src={dishImageUrl(restaurant.restaurant_name, item.item_image)}
                alt={item.item_name}
              />
            )}
            <h4>{item.item_name}</h4>
            {item.item_description && (
              <p>
                {Array.isArray(item.item_description)
                  ? item.item_description.join(", ")
                  : item.item_description}
              </p>
            )}
            <p>Status: {item.availability ? "Available" : "Unavailable"}</p>
            {item.item_cost != null && <p>${item.item_cost}</p>}
          </article>
        ))}
      </section>
    </>
  );
}
