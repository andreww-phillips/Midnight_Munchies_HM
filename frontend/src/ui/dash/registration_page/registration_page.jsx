export default function RegistrationPage() {
  return (
    <>
      <header />
      <nav />
      <main>
        <section id="role-selection-container">
          <fieldset>
            <legend>Select Your Role:</legend>
            <div>
              <input type="radio" id="role-customer" name="role" value="customer" />
              <label htmlFor="role-customer">Customer</label>
            </div>
            <div>
              <input type="radio" id="role-courier" name="role" value="courier" />
              <label htmlFor="role-courier">Courier</label>
            </div>
            <div>
              <input type="radio" id="role-restaurant" name="role" value="restaurant" />
              <label htmlFor="role-restaurant">Restaurant</label>
            </div>
          </fieldset>
        </section>
        <section id="registration-inputs" />
      </main>
      <footer />
    </>
  );
}